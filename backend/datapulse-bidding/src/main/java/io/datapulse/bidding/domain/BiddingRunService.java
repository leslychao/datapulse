package io.datapulse.bidding.domain;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.guard.BiddingGuardChain;
import io.datapulse.bidding.domain.guard.BiddingGuardChain.GuardChainResult;
import io.datapulse.bidding.domain.strategy.BiddingStrategyRegistry;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import io.datapulse.bidding.persistence.BiddingDataReadRepository;
import io.datapulse.bidding.persistence.BiddingRunEntity;
import io.datapulse.bidding.persistence.BiddingRunRepository;
import io.datapulse.bidding.persistence.EligibleProductRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingRunService {

  private final BiddingStrategyRegistry strategyRegistry;
  private final BiddingGuardChain guardChain;
  private final BiddingSignalCollector signalCollector;
  private final BiddingDataReadRepository readRepo;
  private final BidDecisionRepository decisionRepository;
  private final BiddingRunRepository runRepository;
  private final BidPolicyRepository policyRepository;
  private final BiddingActionScheduler actionScheduler;
  private final BiddingProperties properties;
  private final ObjectMapper objectMapper;

  @Transactional
  public void executeRun(long workspaceId, long bidPolicyId) {
    BidPolicyEntity policy = policyRepository.findById(bidPolicyId).orElse(null);
    if (policy == null) {
      log.warn("Bid policy not found: bidPolicyId={}", bidPolicyId);
      return;
    }
    if (policy.getStatus() != BidPolicyStatus.ACTIVE) {
      log.warn("Bid policy is not ACTIVE: bidPolicyId={}, status={}",
          bidPolicyId, policy.getStatus());
      return;
    }

    JsonNode configNode = parseConfig(policy.getConfig());
    BiddingRunEntity run = createRun(workspaceId, bidPolicyId);

    try {
      List<EligibleProductRow> eligible = readRepo.findEligibleProducts(
          workspaceId, bidPolicyId);
      run.setTotalEligible(eligible.size());

      if (eligible.isEmpty()) {
        log.info("Bidding run {}: no eligible products for policy {}",
            run.getId(), bidPolicyId);
        completeRun(run);
        return;
      }

      var strategy = strategyRegistry.resolve(policy.getStrategyType());
      int totalBidUp = 0;
      int totalBidDown = 0;
      int totalHold = 0;
      int totalPause = 0;

      for (EligibleProductRow product : eligible) {
        BiddingSignalSet signals = signalCollector.collect(
            workspaceId,
            product.marketplaceOfferId(),
            product.marketplaceSku(),
            properties.getDefaultLookbackDays());

        if (!signalCollector.hasMinimumData(signals)) {
          log.debug("Insufficient data for offer: marketplaceOfferId={}, skipping",
              product.marketplaceOfferId());
          totalHold++;
          saveDecision(run.getId(), workspaceId, product, policy,
              BiddingStrategyResult.hold(), null, configNode);
          continue;
        }

        BiddingStrategyResult result = strategy.evaluate(signals, configNode);

        if (result.decisionType() == BidDecisionType.HOLD) {
          totalHold++;
          saveDecision(run.getId(), workspaceId, product, policy,
              result, null, configNode);
          continue;
        }

        BiddingGuardContext guardContext = new BiddingGuardContext(
            product.marketplaceOfferId(),
            workspaceId,
            signals,
            result.decisionType(),
            result.targetBid(),
            signals.currentBid(),
            configNode);

        GuardChainResult guardResult = guardChain.evaluate(guardContext);

        if (!guardResult.allPassed()) {
          totalHold++;
          saveDecision(run.getId(), workspaceId, product, policy,
              holdWithGuardInfo(result, guardResult), guardResult, configNode);
          continue;
        }

        saveDecision(run.getId(), workspaceId, product, policy,
            result, guardResult, configNode);

        switch (result.decisionType()) {
          case BID_UP -> totalBidUp++;
          case BID_DOWN -> totalBidDown++;
          case PAUSE -> totalPause++;
          default -> totalHold++;
        }
      }

      run.setTotalDecisions(eligible.size());
      run.setTotalBidUp(totalBidUp);
      run.setTotalBidDown(totalBidDown);
      run.setTotalHold(totalHold);
      run.setTotalPause(totalPause);

      if (isBlastRadiusBreached(totalBidUp, eligible.size())) {
        run.setStatus(BiddingRunStatus.PAUSED);
        run.setErrorMessage("Blast radius breached: totalBidUp=%d exceeds %d%% of %d eligible"
            .formatted(totalBidUp, properties.getMaxBidUpRatioPct(), eligible.size()));
        log.warn("Bidding run paused: runId={}, policy={}, reason=blast_radius, "
                + "bidUp={}, eligible={}",
            run.getId(), bidPolicyId, totalBidUp, eligible.size());
      } else {
        run.setStatus(BiddingRunStatus.COMPLETED);
      }

      run.setCompletedAt(OffsetDateTime.now());
      runRepository.save(run);

      if (run.getStatus() == BiddingRunStatus.COMPLETED) {
        actionScheduler.scheduleActions(run.getId());
      }

      log.info("Bidding run completed: runId={}, policy={}, eligible={}, "
              + "bidUp={}, bidDown={}, hold={}, pause={}, status={}",
          run.getId(), bidPolicyId, eligible.size(),
          totalBidUp, totalBidDown, totalHold, totalPause, run.getStatus());

    } catch (Exception e) {
      log.error("Bidding run failed: runId={}, policy={}, error={}",
          run.getId(), bidPolicyId, e.getMessage(), e);
      run.setStatus(BiddingRunStatus.FAILED);
      run.setErrorMessage(e.getMessage());
      run.setCompletedAt(OffsetDateTime.now());
      runRepository.save(run);
    }
  }

  private BiddingRunEntity createRun(long workspaceId, long bidPolicyId) {
    var run = new BiddingRunEntity();
    run.setWorkspaceId(workspaceId);
    run.setBidPolicyId(bidPolicyId);
    run.setStatus(BiddingRunStatus.RUNNING);
    run.setStartedAt(OffsetDateTime.now());
    return runRepository.save(run);
  }

  private void completeRun(BiddingRunEntity run) {
    run.setStatus(BiddingRunStatus.COMPLETED);
    run.setCompletedAt(OffsetDateTime.now());
    runRepository.save(run);
  }

  private boolean isBlastRadiusBreached(int totalBidUp, int totalEligible) {
    if (totalEligible == 0) {
      return false;
    }
    int threshold = totalEligible * properties.getMaxBidUpRatioPct() / 100;
    return totalBidUp > threshold;
  }

  private BiddingStrategyResult holdWithGuardInfo(
      BiddingStrategyResult original, GuardChainResult guardResult) {
    String guardExplanation = guardResult.blockingGuard() != null
        ? "Blocked by guard: %s (%s)".formatted(
            guardResult.blockingGuard().guardName(),
            guardResult.blockingGuard().messageKey())
        : original.explanation();
    return new BiddingStrategyResult(
        BidDecisionType.HOLD, null, guardExplanation);
  }

  private void saveDecision(
      long biddingRunId,
      long workspaceId,
      EligibleProductRow product,
      BidPolicyEntity policy,
      BiddingStrategyResult result,
      GuardChainResult guardResult,
      JsonNode configNode) {

    var entity = new BidDecisionEntity();
    entity.setBiddingRunId(biddingRunId);
    entity.setWorkspaceId(workspaceId);
    entity.setMarketplaceOfferId(product.marketplaceOfferId());
    entity.setBidPolicyId(policy.getId());
    entity.setStrategyType(policy.getStrategyType());
    entity.setDecisionType(result.decisionType());
    entity.setTargetBid(result.targetBid());
    entity.setExecutionMode(policy.getExecutionMode().name());
    entity.setExplanationSummary(result.explanation());
    entity.setSignalSnapshot(serializeJson(configNode));
    entity.setGuardsApplied(
        guardResult != null ? serializeJson(guardResult.evaluations()) : null);

    decisionRepository.save(entity);
  }

  private JsonNode parseConfig(String configJson) {
    if (configJson == null || configJson.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(configJson);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse bid policy config, using empty: {}", e.getMessage());
      return objectMapper.createObjectNode();
    }
  }

  private String serializeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      log.error("JSON serialization failed", e);
      return "{}";
    }
  }
}
