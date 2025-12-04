package io.datapulse.etl.nextgen.dto;

import java.util.List;

public record MaterializationRequest(
    String eventId,
    List<ExecutionStatus> executionStatuses
) {
}
