package io.datapulse.analytics.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public record PnlFilter(
        LocalDate from,
        LocalDate to,
        String period,
        Long sellerSkuId,
        String search,
        String sourcePlatform
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
}
