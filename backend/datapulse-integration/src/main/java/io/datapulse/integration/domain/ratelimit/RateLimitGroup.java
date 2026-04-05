package io.datapulse.integration.domain.ratelimit;

import io.datapulse.integration.domain.MarketplaceType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RateLimitGroup {

    // Wildberries groups (rates in req/s)
    WB_STATISTICS(1.0 / 60.0, 1, MarketplaceType.WB),
    WB_ANALYTICS(1.0 / 20.0, 1, MarketplaceType.WB),
    WB_FINANCE(1.0 / 60.0, 1, MarketplaceType.WB),
    WB_PROMO(10.0 / 6.0, 5, MarketplaceType.WB),
    WB_PROMO_NOMENCLATURES(10.0 / 6.0, 5, MarketplaceType.WB),
    WB_PRICE_UPDATE(5.0 / 60.0, 1, MarketplaceType.WB),
    WB_CONTENT(1.0 / 10.0, 1, MarketplaceType.WB),
    WB_PRICES_READ(1.0 / 10.0, 1, MarketplaceType.WB),
    WB_MARKETPLACE(1.0 / 10.0, 1, MarketplaceType.WB),

    // Ozon groups
    OZON_DEFAULT(30.0 / 60.0, 3, MarketplaceType.OZON),
    OZON_PROMO(20.0 / 60.0, 3, MarketplaceType.OZON),
    OZON_PRICE_UPDATE(30.0 / 60.0, 3, MarketplaceType.OZON),
    OZON_PERFORMANCE(60.0 / 60.0, 5, MarketplaceType.OZON);

    private final double initialRate;
    private final int burst;
    private final MarketplaceType marketplaceType;
}
