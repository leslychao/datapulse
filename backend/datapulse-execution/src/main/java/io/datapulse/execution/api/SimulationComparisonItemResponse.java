package io.datapulse.execution.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SimulationComparisonItemResponse(
        long marketplaceOfferId,
        String marketplaceSku,
        BigDecimal simulatedPrice,
        BigDecimal canonicalPriceAtSimulation,
        BigDecimal currentRealPrice,
        BigDecimal priceDelta,
        BigDecimal priceDeltaPct,
        BigDecimal previousSimulatedPrice,
        OffsetDateTime simulatedAt,
        long priceActionId
) {
}
