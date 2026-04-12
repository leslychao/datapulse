package io.datapulse.bidding.domain;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

import io.datapulse.bidding.persistence.BidActionRepository;
import io.datapulse.bidding.persistence.BidPolicyAssignmentRepository;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import io.datapulse.bidding.persistence.BiddingRunRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidPolicyService {

  private static final int FULL_AUTO_MIN_SUCCESSFUL_RUNS = 5;
  private static final int FULL_AUTO_LOOKBACK_DAYS = 7;

  private final BidPolicyRepository policyRepository;
  private final BidPolicyAssignmentRepository assignmentRepository;
  private final BiddingRunRepository runRepository;
  private final BidActionRepository actionRepository;
  private final BidPolicyConfigValidator configValidator;

  @Transactional
  public BidPolicyEntity createPolicy(
      long workspaceId,
      String name,
      BiddingStrategyType strategyType,
      ExecutionMode executionMode,
      JsonNode config,
      Long createdBy) {

    configValidator.validate(strategyType, config);

    var entity = new BidPolicyEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setName(name);
    entity.setStrategyType(strategyType);
    entity.setExecutionMode(executionMode);
    entity.setStatus(BidPolicyStatus.DRAFT);
    entity.setConfig(config.toString());
    entity.setCreatedBy(createdBy);

    BidPolicyEntity saved = policyRepository.save(entity);
    log.info("Bid policy created: id={}, workspace={}, strategy={}",
        saved.getId(), workspaceId, strategyType);
    return saved;
  }

  @Transactional
  public BidPolicyEntity updatePolicy(
      long id,
      String name,
      ExecutionMode executionMode,
      JsonNode config) {

    BidPolicyEntity entity = requirePolicy(id);
    ensureNotArchived(entity);

    configValidator.validate(entity.getStrategyType(), config);

    if (executionMode == ExecutionMode.FULL_AUTO
        && entity.getExecutionMode() != ExecutionMode.FULL_AUTO) {
      ensureFullAutoSafetyGate(entity);
    }

    entity.setName(name);
    entity.setExecutionMode(executionMode);
    entity.setConfig(config.toString());

    log.info("Bid policy updated: id={}", id);
    return policyRepository.save(entity);
  }

  @Transactional
  public void activatePolicy(long id) {
    BidPolicyEntity entity = requirePolicy(id);
    ensureNotArchived(entity);

    if (entity.getStatus() == BidPolicyStatus.ACTIVE) {
      throw BadRequestException.of(MessageCodes.BIDDING_POLICY_ALREADY_ACTIVE);
    }

    entity.setStatus(BidPolicyStatus.ACTIVE);
    policyRepository.save(entity);
    log.info("Bid policy activated: id={}", id);
  }

  @Transactional
  public void pausePolicy(long id) {
    BidPolicyEntity entity = requirePolicy(id);

    if (entity.getStatus() == BidPolicyStatus.PAUSED) {
      throw BadRequestException.of(MessageCodes.BIDDING_POLICY_ALREADY_PAUSED);
    }
    if (entity.getStatus() != BidPolicyStatus.ACTIVE) {
      throw BadRequestException.of(MessageCodes.BIDDING_POLICY_ARCHIVED);
    }

    entity.setStatus(BidPolicyStatus.PAUSED);
    policyRepository.save(entity);
    log.info("Bid policy paused: id={}", id);
  }

  @Transactional
  public void archivePolicy(long id) {
    BidPolicyEntity entity = requirePolicy(id);
    entity.setStatus(BidPolicyStatus.ARCHIVED);
    policyRepository.save(entity);
    log.info("Bid policy archived: id={}", id);
  }

  @Transactional(readOnly = true)
  public BidPolicyEntity getPolicy(long id) {
    return requirePolicy(id);
  }

  @Transactional(readOnly = true)
  public Page<BidPolicyEntity> listPolicies(long workspaceId, Pageable pageable) {
    return policyRepository.findByWorkspaceId(workspaceId, pageable);
  }

  @Transactional(readOnly = true)
  public int countAssignments(long bidPolicyId) {
    return assignmentRepository.countByBidPolicyId(bidPolicyId);
  }

  private BidPolicyEntity requirePolicy(long id) {
    return policyRepository.findById(id)
        .orElseThrow(() -> NotFoundException.of(MessageCodes.BIDDING_POLICY_NOT_FOUND, id));
  }

  private void ensureNotArchived(BidPolicyEntity entity) {
    if (entity.getStatus() == BidPolicyStatus.ARCHIVED) {
      throw BadRequestException.of(MessageCodes.BIDDING_POLICY_ARCHIVED);
    }
  }

  private void ensureFullAutoSafetyGate(BidPolicyEntity entity) {
    OffsetDateTime since = OffsetDateTime.now()
        .minusDays(FULL_AUTO_LOOKBACK_DAYS);

    long completedRuns = runRepository.countByPolicyIdAndStatusSince(
        entity.getId(), BiddingRunStatus.COMPLETED, since);
    if (completedRuns < FULL_AUTO_MIN_SUCCESSFUL_RUNS) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_FULL_AUTO_INSUFFICIENT_RUNS,
          completedRuns, FULL_AUTO_MIN_SUCCESSFUL_RUNS,
          FULL_AUTO_LOOKBACK_DAYS);
    }

    long failedRuns = runRepository.countByPolicyIdAndStatusSince(
        entity.getId(), BiddingRunStatus.FAILED, since);
    if (failedRuns > 0) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_FULL_AUTO_HAS_FAILURES, failedRuns);
    }

    long failedActions = actionRepository
        .countByStatusAndPolicySince(entity.getId(),
            BidActionStatus.FAILED, since);
    if (failedActions > 0) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_FULL_AUTO_HAS_FAILED_ACTIONS,
          failedActions);
    }
  }
}
