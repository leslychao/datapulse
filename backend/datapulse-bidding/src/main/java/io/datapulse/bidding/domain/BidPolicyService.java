package io.datapulse.bidding.domain;

import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
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

  private final BidPolicyRepository policyRepository;

  @Transactional
  public BidPolicyEntity createPolicy(
      long workspaceId,
      String name,
      BiddingStrategyType strategyType,
      ExecutionMode executionMode,
      String configJson,
      Long createdBy) {

    var entity = new BidPolicyEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setName(name);
    entity.setStrategyType(strategyType);
    entity.setExecutionMode(executionMode);
    entity.setStatus(BidPolicyStatus.DRAFT);
    entity.setConfig(configJson);
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
      String configJson) {

    BidPolicyEntity entity = requirePolicy(id);
    ensureNotArchived(entity);

    entity.setName(name);
    entity.setExecutionMode(executionMode);
    entity.setConfig(configJson);

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

  private BidPolicyEntity requirePolicy(long id) {
    return policyRepository.findById(id)
        .orElseThrow(() -> NotFoundException.of(MessageCodes.BIDDING_POLICY_NOT_FOUND, id));
  }

  private void ensureNotArchived(BidPolicyEntity entity) {
    if (entity.getStatus() == BidPolicyStatus.ARCHIVED) {
      throw BadRequestException.of(MessageCodes.BIDDING_POLICY_ARCHIVED);
    }
  }
}
