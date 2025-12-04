package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.EventStatus;
import io.datapulse.etl.nextgen.dto.ExecutionStatus;
import io.datapulse.etl.nextgen.dto.MaterializationRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MaterializationService {

  private static final Logger log = LoggerFactory.getLogger(MaterializationService.class);

  public EventStatus materialize(MaterializationRequest request) {
    List<ExecutionStatus> statuses = request.executionStatuses();
    boolean hasSuccess = statuses.contains(ExecutionStatus.SUCCESS);
    boolean hasFailed = statuses.contains(ExecutionStatus.FAILED_FINAL);
    boolean allNoData = statuses.stream().allMatch(status -> status == ExecutionStatus.NO_DATA);
    boolean allFailed = statuses.stream().allMatch(status -> status == ExecutionStatus.FAILED_FINAL);
    EventStatus status;
    if (hasSuccess && hasFailed) {
      status = EventStatus.PARTIAL_SUCCESS;
    } else if (hasSuccess) {
      status = EventStatus.SUCCESS;
    } else if (allNoData) {
      status = EventStatus.NO_DATA;
    } else if (allFailed) {
      status = EventStatus.FAILED;
    } else {
      status = EventStatus.CANCELLED;
    }
    log.info("materialization for event {} resolved status {}", request.eventId(), status);
    return status;
  }
}
