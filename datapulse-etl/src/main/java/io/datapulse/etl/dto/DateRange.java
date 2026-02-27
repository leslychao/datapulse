package io.datapulse.etl.dto;

import java.time.LocalDate;

public record DateRange(
    LocalDate dateFrom,
    LocalDate dateTo
) {

}
