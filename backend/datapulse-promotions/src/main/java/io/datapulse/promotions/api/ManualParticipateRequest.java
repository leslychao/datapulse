package io.datapulse.promotions.api;

import java.math.BigDecimal;

public record ManualParticipateRequest(
        BigDecimal targetPromoPrice
) {
}
