package io.datapulse.etl.v1.dto;

import java.time.LocalDate;

public record EtlDateRange(
    LocalDate dateFrom,
    LocalDate dateTo
) {

}
