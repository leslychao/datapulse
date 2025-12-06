package io.datapulse.etl.dto;

import java.time.LocalDate;

public record EtlRunRequest(
    Long accountId,
    String event,
    LocalDate dateFrom,
    LocalDate dateTo,
    Integer burst
) {

}
