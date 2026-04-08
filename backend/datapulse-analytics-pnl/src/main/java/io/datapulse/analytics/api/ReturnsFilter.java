package io.datapulse.analytics.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public record ReturnsFilter(
        LocalDate from,
        LocalDate to,
        String period,
        String search,
        TrendGranularity granularity
) {

    /**
     * Frontend sends "YYYY-MM", ClickHouse stores period as UInt32 YYYYMM.
     */
    public Integer periodAsInt() {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            YearMonth ym = YearMonth.parse(period);
            return ym.getYear() * 100 + ym.getMonthValue();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public TrendGranularity granularityOrDefault() {
        return granularity != null ? granularity : TrendGranularity.MONTHLY;
    }
}
