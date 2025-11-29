package io.datapulse.etl.dto;

import java.time.LocalDate;

public record EtlRunRequest(
    Long accountId,
    String event,
    LocalDate from,
    LocalDate to,
    Integer burst
) {

}
