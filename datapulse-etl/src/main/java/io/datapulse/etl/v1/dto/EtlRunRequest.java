package io.datapulse.etl.v1.dto;

import java.time.LocalDate;
import java.util.List;

public record EtlRunRequest(
    Long accountId,
    String event,
    EtlDateMode dateMode,
    LocalDate dateFrom,
    LocalDate dateTo,
    Integer lastDays,
    Integer burst,
    List<String> sourceIds
) {

}
