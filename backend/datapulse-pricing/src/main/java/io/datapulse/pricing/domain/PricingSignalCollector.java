package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.datapulse.pricing.persistence.PricingClickHouseReadRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch-assembles PricingSignalSet for all eligible offers in a pricing run.
 * One query per signal type — avoids N+1.
 * <p>
 * ClickHouse-based signals (avg_commission_pct, avg_logistics_per_unit, return_rate_pct)
 * are not available in this phase; they return null. The strategy handles missing signals
 * via commission_source / logistics_source fallback to manual values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingSignalCollector {

    private static final int AD_COST_LOOKBACK_DAYS = 30;

    private final PricingDataReadRepository dataReadRepository;
    private final PricingClickHouseReadRepository clickHouseReadRepository;

    public Map<Long, PricingSignalSet> collectBatch(List<Long> offerIds, long connectionId,
                                                    int volatilityPeriodDays) {
        if (offerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, BigDecimal> prices = dataReadRepository.findCurrentPrices(offerIds);
        Map<Long, BigDecimal> cogs = dataReadRepository.findCurrentCogs(offerIds);
        Map<Long, Integer> stock = dataReadRepository.findTotalStock(offerIds);
        Set<Long> lockedIds = Set.copyOf(dataReadRepository.findLockedOfferIds(offerIds));
        OffsetDateTime dataFreshness = dataReadRepository.findDataFreshness(connectionId);
        Map<Long, OffsetDateTime> lastChanges = dataReadRepository.findLatestChangeDecisions(offerIds);

        OffsetDateTime volatilitySince = OffsetDateTime.now().minusDays(volatilityPeriodDays);
        Map<Long, Integer> reversals = dataReadRepository.findPriceReversals(offerIds, volatilitySince);
        Set<Long> promoActiveIds = Set.copyOf(dataReadRepository.findPromoActiveOfferIds(offerIds));

        Map<Long, BigDecimal> adCostRatios = collectAdCostRatios(offerIds);

        Map<Long, PricingSignalSet> result = new HashMap<>();

        for (Long offerId : offerIds) {
            result.put(offerId, new PricingSignalSet(
                    prices.get(offerId),
                    cogs.get(offerId),
                    null,
                    stock.get(offerId),
                    lockedIds.contains(offerId),
                    promoActiveIds.contains(offerId),
                    null,
                    null,
                    null,
                    adCostRatios.get(offerId),
                    lastChanges.get(offerId),
                    reversals.get(offerId),
                    dataFreshness,
                    null
            ));
        }

        return result;
    }

    /**
     * Fetches ad_cost_ratio from ClickHouse keyed by marketplace_sku,
     * then maps results back to offerId.
     * Returns empty map if ClickHouse is unavailable (OPTIONAL signal — safe default is null/0).
     */
    private Map<Long, BigDecimal> collectAdCostRatios(List<Long> offerIds) {
        try {
            Map<Long, String> offerSkus = dataReadRepository.findMarketplaceSkus(offerIds);
            List<String> skus = new ArrayList<>(Set.copyOf(offerSkus.values()));
            if (skus.isEmpty()) {
                return Collections.emptyMap();
            }

            Map<String, BigDecimal> skuRatios =
                    clickHouseReadRepository.findAdCostRatios(skus, AD_COST_LOOKBACK_DAYS);

            Map<Long, BigDecimal> result = new HashMap<>();
            for (var entry : offerSkus.entrySet()) {
                BigDecimal ratio = skuRatios.get(entry.getValue());
                if (ratio != null) {
                    result.put(entry.getKey(), ratio);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("ClickHouse ad_cost_ratio query failed, using null fallback: {}",
                    e.getMessage());
            return Collections.emptyMap();
        }
    }
}
