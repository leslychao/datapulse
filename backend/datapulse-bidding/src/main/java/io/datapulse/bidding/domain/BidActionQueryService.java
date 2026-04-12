package io.datapulse.bidding.domain;

import java.util.List;

import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.bidding.persistence.BidActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BidActionQueryService {

  private final BidActionRepository actionRepository;

  @Transactional(readOnly = true)
  public Page<BidActionEntity> listActions(
      long workspaceId,
      List<BidActionStatus> statuses,
      String executionMode,
      Pageable pageable) {

    boolean hasStatuses = statuses != null && !statuses.isEmpty();
    boolean hasMode = executionMode != null && !executionMode.isBlank();

    if (hasStatuses && hasMode) {
      return actionRepository.findByWorkspaceIdAndStatusInAndExecutionMode(
          workspaceId, statuses, executionMode, pageable);
    }
    if (hasStatuses) {
      return actionRepository.findByWorkspaceIdAndStatusIn(
          workspaceId, statuses, pageable);
    }
    if (hasMode) {
      return actionRepository.findByWorkspaceIdAndExecutionMode(
          workspaceId, executionMode, pageable);
    }
    return actionRepository.findByWorkspaceId(workspaceId, pageable);
  }
}
