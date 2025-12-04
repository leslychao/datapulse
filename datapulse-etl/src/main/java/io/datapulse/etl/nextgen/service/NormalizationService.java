package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.ExecutionResult;
import io.datapulse.etl.nextgen.dto.ExecutionStatus;
import io.datapulse.etl.nextgen.dto.NormalizationPayload;
import io.datapulse.etl.nextgen.service.ExecutionLifecycleService;
import org.springframework.stereotype.Service;

@Service
public class NormalizationService {

  private final ExecutionLifecycleService executionLifecycleService;

  public NormalizationService(ExecutionLifecycleService executionLifecycleService) {
    this.executionLifecycleService = executionLifecycleService;
  }

  public ExecutionResult normalize(NormalizationPayload payload) {
    long rawRows = payload.rawRowsCount();
    if (rawRows > 0) {
      executionLifecycleService.markSuccess(payload.executionId());
    } else {
      executionLifecycleService.markNoData(payload.executionId());
    }
    ExecutionStatus status = executionLifecycleService.status(payload.executionId());
    return executionLifecycleService.buildResult(
        payload.executionId(),
        payload.eventId(),
        rawRows,
        null,
        null
    );
  }
}
