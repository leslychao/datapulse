package io.datapulse.bidding.domain;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.ApplicationEventPublisher;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.domain.event.BiddingRunCompletedEvent;
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
  private final BiddingResumeEvaluator resumeEvaluator;
  private final BiddingProperties properties;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher eventPublisher;

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

    if (runRepository.existsByBidPolicyIdAndStatus(
        bidPolicyId, BiddingRunStatus.IN_PROGRESS)) {
      log.warn("Concurrent run blocked: bidPolicyId={} already has an IN_PROGRESS run",
          bidPolicyId);
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
        eventPublisher.publishEvent(new BiddingRunCompletedEvent(
            workspaceId, run.getId(), bidPolicyId,
            run.getStatus().name(), 0, 0, 0, 0));
        return;
      }

      var strategy = strategyRegistry.resolve(policy.getStrategyType());
      int totalBidUp = 0;
      int totalBidDown = 0;
      int totalHold = 0;
      int totalPause = 0;
      int maxAbsChangePctObserved = 0;

      for (EligibleProductRow product : eligible) {
        BiddingSignalSet signals = signalCollector.collect(
            workspaceId,
            product.marketplaceOfferId(),
            product.marketplaceSku(),
            product.connectionId(),
            properties.getDefaultLookbackDays());

        if (!signalCollector.hasMinimumData(signals)) {
          log.debug("Insufficient data for offer: marketplaceOfferId={}, skipping",
              product.marketplaceOfferId());
          totalHold++;
          saveDecision(run.getId(), workspaceId, product, policy,
              BiddingStrategyResult.hold(), null, signals);
          continue;
        }

        BiddingStrategyResult resumeResult = resumeEvaluator.evaluateResume(
            workspaceId, product.marketplaceOfferId(), signals);
        if (resumeResult != null) {
          saveDecision(run.getId(), workspaceId, product, policy,
              resumeResult, null, signals);
          totalHold++;
          continue;
        }

        BiddingStrategyResult result = strategy.evaluate(signals, configNode);

        if (result.suggestedTransition() != null) {
          eventPublisher.publishEvent(
              new io.datapulse.bidding.domain.event.LaunchTransitionRequestedEvent(
                  workspaceId,
                  product.marketplaceOfferId(),
                  bidPolicyId,
                  result.suggestedTransition()));
        }

        if (result.decisionType() == BidDecisionType.HOLD) {
          totalHold++;
          saveDecision(run.getId(), workspaceId, product, policy,
              result, null, signals);
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

        boolean isDefensiveAction =
            result.decisionType() == BidDecisionType.BID_DOWN
                || result.decisionType() == BidDecisionType.PAUSE;

        if (!guardResult.allPassed() && !isDefensiveAction) {
          totalHold++;
          saveDecision(run.getId(), workspaceId, product, policy,
              holdWithGuardInfo(result, guardResult), guardResult, signals);
          continue;
        }

        saveDecision(run.getId(), workspaceId, product, policy,
            result, guardResult, signals);

        switch (result.decisionType()) {
          case BID_UP -> totalBidUp++;
          case BID_DOWN -> totalBidDown++;
          case PAUSE -> totalPause++;
          default -> totalHold++;
        }

        if (signals.currentBid() != null && result.targetBid() != null
            && signals.currentBid() > 0) {
          int changePct = Math.abs(
              (result.targetBid() - signals.currentBid()) * 100
                  / signals.currentBid());
          maxAbsChangePctObserved = Math.max(
              maxAbsChangePctObserved, changePct);
        }
      }

      run.setTotalDecisions(eligible.size());
      run.setTotalBidUp(totalBidUp);
      run.setTotalBidDown(totalBidDown);
      run.setTotalHold(totalHold);
      run.setTotalPause(totalPause);

      String blastRadiusReason = checkBlastRadius(
          totalBidUp, eligible.size(), maxAbsChangePctObserved,
          policy.getExecutionMode());
      if (blastRadiusReason != null) {
        run.setStatus(BiddingRunStatus.PAUSED);
        run.setErrorMessage(blastRadiusReason);
        log.warn("Bidding run paused: runId={}, policy={}, reason=blast_radius, "
                + "bidUp={}, eligible={}, maxChange={}%",
            run.getId(), bidPolicyId, totalBidUp,
            eligible.size(), maxAbsChangePctObserved);
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

      eventPublisher.publishEvent(new BiddingRunCompletedEvent(
          workspaceId, run.getId(), bidPolicyId,
          run.getStatus().name(),
          totalBidUp, totalBidDown, totalHold, totalPause));

    } catch (Exception e) {
      log.error("Bidding run failed: runId={}, policy={}, error={}",
          run.getId(), bidPolicyId, e.getMessage(), e);
      run.setStatus(BiddingRunStatus.FAILED);
      run.setErrorMessage(e.getMessage());
      run.setCompletedAt(OffsetDateTime.now());
      runRepository.save(run);
      eventPublisher.publishEvent(new BiddingRunCompletedEvent(
          workspaceId, run.getId(), bidPolicyId,
          run.getStatus().name(), 0, 0, 0, 0));
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

  private String checkBlastRadius(int totalBidUp, int totalEligible,
      int maxAbsChangePct, ExecutionMode executionMode) {
    int bidUpRatioPct = properties.getMaxBidUpRatioPct();
    int absChangePctLimit = properties.getMaxAbsChangePct();
    if (executionMode == ExecutionMode.FULL_AUTO) {
      bidUpRatioPct = bidUpRatioPct / 2;
      absChangePctLimit = absChangePctLimit > 0
          ? absChangePctLimit / 2 : absChangePctLimit;
    }
    if (totalEligible > 0) {
      int threshold = totalEligible * bidUpRatioPct / 100;
      if (totalBidUp > threshold) {
        return "Blast radius breached: totalBidUp=%d exceeds %d%% of %d eligible"
            .formatted(totalBidUp, bidUpRatioPct, totalEligible);
      }
    }
    if (absChangePctLimit > 0 && maxAbsChangePct > absChangePctLimit) {
      return "Blast radius breached: maxAbsChangePct=%d%% exceeds limit %d%%"
          .formatted(maxAbsChangePct, absChangePctLimit);
    }
    return null;
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
      BiddingSignalSet signals) {

    var entity = new BidDecisionEntity();
    entity.setBiddingRunId(biddingRunId);
    entity.setWorkspaceId(workspaceId);
    entity.setMarketplaceOfferId(product.marketplaceOfferId());
    entity.setBidPolicyId(policy.getId());
    entity.setStrategyType(policy.getStrategyType());
    entity.setDecisionType(result.decisionType());
    entity.setCurrentBid(signals.currentBid());
    entity.setTargetBid(result.targetBid());
    entity.setExecutionMode(policy.getExecutionMode().name());
    entity.setExplanationSummary(result.explanation());
    entity.setSignalSnapshot(serializeJson(signals));
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
