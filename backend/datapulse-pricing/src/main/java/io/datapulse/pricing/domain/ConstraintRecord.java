package io.datapulse.pricing.domain;

import java.math.BigDecimal;

public record ConstraintRecord(
        String name,
        BigDecimal fromPrice,
        BigDecimal toPrice
) {
}
