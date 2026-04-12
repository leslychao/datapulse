package io.datapulse.bidding.domain;

import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BidPolicyRepository;
import io.datapulse.bidding.persistence.BiddingRunEntity;
import io.datapulse.bidding.persistence.BiddingRunRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingRunApiService {

  private static final String BIDDING_RUN_AGGREGATE_TYPE = "bidding_run";

  private final BidPolicyRepository policyRepository;
  private final BiddingRunRepository runRepository;
  private final OutboxService outboxService;

  @Transactional
  public void triggerRun(long workspaceId, long bidPolicyId) {
    BidPolicyEntity policy = policyRepository.findById(bidPolicyId)
        .orElseThrow(() -> NotFoundException.of(MessageCodes.BIDDING_POLICY_NOT_FOUND,
            bidPolicyId));

    outboxService.createEvent(
        OutboxEventType.BIDDING_RUN_EXECUTE,
        BIDDING_RUN_AGGREGATE_TYPE,
        policy.getId(),
        Map.of(
            "workspaceId", workspaceId,
            "bidPolicyId", policy.getId()));

    log.info("Bidding run triggered manually: workspaceId={}, bidPolicyId={}",
        workspaceId, bidPolicyId);
  }

  @Transactional(readOnly = true)
  public Page<BiddingRunEntity> listRuns(long workspaceId, Pageable pageable) {
    return runRepository.findByWorkspaceId(workspaceId, pageable);
  }
}
