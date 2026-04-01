package io.datapulse.analytics.api;

import java.time.LocalDate;

public record PnlFilter(
        Long connectionId,
        LocalDate from,
        LocalDate to,
        Integer period,
        Long sellerSkuId,
        String search
) {}
