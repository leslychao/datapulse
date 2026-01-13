package io.datapulse.etl.dto;

import java.time.LocalDate;

public record EtlDateRange(
    LocalDate dateFrom,
    LocalDate dateTo
) {

}
