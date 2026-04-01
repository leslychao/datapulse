package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.datapulse.pricing.persistence.PricingDataReadRepository;
import lombok.RequiredArgsConstructor;

/**
 * Batch-assembles PricingSignalSet for all eligible offers in a pricing run.
 * One query per signal type — avoids N+1.
 * <p>
 * ClickHouse-based signals (avg_commission_pct, avg_logistics_per_unit, return_rate_pct, ad_cost_ratio)
 * are not available in this phase; they return null. The strategy handles missing signals
 * via commission_source / logistics_source fallback to manual values.
 */
@Service
@RequiredArgsConstructor
public class PricingSignalCollector {

    private final PricingDataReadRepository dataReadRepository;

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

        Map<Long, PricingSignalSet> result = new HashMap<>();

        for (Long offerId : offerIds) {
            result.put(offerId, new PricingSignalSet(
                    prices.get(offerId),
                    cogs.get(offerId),
                    null,
                    stock.get(offerId),
                    lockedIds.contains(offerId),
                    null,
                    null,
                    null,
                    null,
                    lastChanges.get(offerId),
                    reversals.get(offerId),
                    dataFreshness
            ));
        }

        return result;
    }
}
