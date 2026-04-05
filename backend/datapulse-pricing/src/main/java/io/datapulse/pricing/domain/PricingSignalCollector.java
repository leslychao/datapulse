package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.datapulse.pricing.persistence.PricingClickHouseReadRepository;
import io.datapulse.pricing.persistence.PricingClickHouseReadRepository.CommissionResult;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch-assembles PricingSignalSet for all eligible offers in a pricing run.
 * One query per signal type — avoids N+1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingSignalCollector {

    static final int AD_COST_LOOKBACK_DAYS = 30;
    static final int COMMISSION_LOOKBACK_DAYS = 30;
    static final int COMMISSION_MIN_TRANSACTIONS = 5;
    static final int LOGISTICS_LOOKBACK_DAYS = 30;
    static final int RETURNS_LOOKBACK_DAYS = 30;

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
        Map<Long, OffsetDateTime> lastChanges =
                dataReadRepository.findLatestChangeDecisions(offerIds);

        OffsetDateTime volatilitySince = OffsetDateTime.now().minusDays(volatilityPeriodDays);
        Map<Long, Integer> reversals =
                dataReadRepository.findPriceReversals(offerIds, volatilitySince);
        Set<Long> promoActiveIds =
                Set.copyOf(dataReadRepository.findPromoActiveOfferIds(offerIds));

        Map<Long, BigDecimal> adCostRatios = collectAdCostRatios(offerIds);

        Map<Long, Long> offerToSku = dataReadRepository.findSellerSkuIds(offerIds);
        ClickHouseSignals chSignals = collectClickHouseSignals(connectionId, offerToSku);

        Map<Long, PricingSignalSet> result = new HashMap<>();

        for (Long offerId : offerIds) {
            result.put(offerId, new PricingSignalSet(
                    prices.get(offerId),
                    cogs.get(offerId),
                    null,
                    stock.get(offerId),
                    lockedIds.contains(offerId),
                    promoActiveIds.contains(offerId),
                    chSignals.commissions.get(offerId),
                    chSignals.logistics.get(offerId),
                    chSignals.returnRates.get(offerId),
                    adCostRatios.get(offerId),
                    lastChanges.get(offerId),
                    reversals.get(offerId),
                    dataFreshness,
                    null
            ));
        }

        return result;
    }

    record ClickHouseSignals(
            Map<Long, BigDecimal> commissions,
            Map<Long, BigDecimal> logistics,
            Map<Long, BigDecimal> returnRates) {

        static final ClickHouseSignals EMPTY =
                new ClickHouseSignals(Map.of(), Map.of(), Map.of());
    }

    /**
     * Fetches commission, logistics, and return rate signals from ClickHouse in batch,
     * keyed by sellerSkuId, then maps results back to offerId.
     * Commission uses a two-level cascade: per-SKU → per-category fallback.
     */
    ClickHouseSignals collectClickHouseSignals(
            long connectionId, Map<Long, Long> offerToSku) {
        if (offerToSku.isEmpty()) {
            return ClickHouseSignals.EMPTY;
        }

        try {
            List<Long> sellerSkuIds = new ArrayList<>(Set.copyOf(offerToSku.values()));

            Map<Long, BigDecimal> commissions =
                    collectCommissions(connectionId, offerToSku, sellerSkuIds);
            Map<Long, BigDecimal> logistics =
                    collectLogistics(connectionId, offerToSku, sellerSkuIds);
            Map<Long, BigDecimal> returnRates =
                    collectReturnRates(connectionId, offerToSku, sellerSkuIds);

            return new ClickHouseSignals(commissions, logistics, returnRates);
        } catch (Exception e) {
            log.warn("ClickHouse pricing signals query failed, using null fallback: {}",
                    e.getMessage());
            return ClickHouseSignals.EMPTY;
        }
    }

    private Map<Long, BigDecimal> collectCommissions(
            long connectionId, Map<Long, Long> offerToSku, List<Long> sellerSkuIds) {
        Map<Long, CommissionResult> perSku =
                clickHouseReadRepository.findAvgCommissionPct(
                        connectionId, sellerSkuIds,
                        COMMISSION_LOOKBACK_DAYS, COMMISSION_MIN_TRANSACTIONS);

        Map<Long, BigDecimal> result = new HashMap<>();
        List<Long> fallbackSkuIds = new ArrayList<>();

        for (var entry : offerToSku.entrySet()) {
            long offerId = entry.getKey();
            long skuId = entry.getValue();
            CommissionResult cr = perSku.get(skuId);
            if (cr != null && cr.commissionPct() != null) {
                result.put(offerId, cr.commissionPct());
            } else {
                fallbackSkuIds.add(skuId);
            }
        }

        if (fallbackSkuIds.isEmpty()) {
            return result;
        }

        Map<Long, String> skuCategories =
                clickHouseReadRepository.findCategoriesBySellerSkuIds(
                        connectionId, fallbackSkuIds);

        List<String> categories = skuCategories.values().stream()
                .distinct()
                .collect(Collectors.toList());

        if (categories.isEmpty()) {
            return result;
        }

        Map<String, BigDecimal> categoryCommissions =
                clickHouseReadRepository.findCategoryAvgCommissionPct(
                        connectionId, categories, COMMISSION_LOOKBACK_DAYS);

        for (var entry : offerToSku.entrySet()) {
            long offerId = entry.getKey();
            if (result.containsKey(offerId)) {
                continue;
            }
            String category = skuCategories.get(entry.getValue());
            if (category != null) {
                BigDecimal catPct = categoryCommissions.get(category);
                if (catPct != null) {
                    result.put(offerId, catPct);
                }
            }
        }

        return result;
    }

    private Map<Long, BigDecimal> collectLogistics(
            long connectionId, Map<Long, Long> offerToSku, List<Long> sellerSkuIds) {
        Map<Long, BigDecimal> perSku =
                clickHouseReadRepository.findAvgLogisticsPerUnit(
                        connectionId, sellerSkuIds, LOGISTICS_LOOKBACK_DAYS);
        return mapSkuResultsToOfferIds(offerToSku, perSku);
    }

    private Map<Long, BigDecimal> collectReturnRates(
            long connectionId, Map<Long, Long> offerToSku, List<Long> sellerSkuIds) {
        Map<Long, BigDecimal> perSku =
                clickHouseReadRepository.findReturnRatePct(
                        connectionId, sellerSkuIds, RETURNS_LOOKBACK_DAYS);
        return mapSkuResultsToOfferIds(offerToSku, perSku);
    }

    private Map<Long, BigDecimal> mapSkuResultsToOfferIds(
            Map<Long, Long> offerToSku, Map<Long, BigDecimal> skuResults) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (var entry : offerToSku.entrySet()) {
            BigDecimal value = skuResults.get(entry.getValue());
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

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
