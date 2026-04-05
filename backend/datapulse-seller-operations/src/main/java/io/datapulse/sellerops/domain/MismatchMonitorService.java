package io.datapulse.sellerops.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.platform.audit.AlertTriggeredEvent;
import io.datapulse.sellerops.config.MismatchProperties;
import io.datapulse.sellerops.persistence.FinanceMismatchJdbcRepository;
import io.datapulse.sellerops.persistence.FinanceMismatchJdbcRepository.FinanceGapCandidate;
import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.MismatchJdbcRepository;
import io.datapulse.sellerops.persistence.PriceMismatchJdbcRepository;
import io.datapulse.sellerops.persistence.PriceMismatchJdbcRepository.PriceMismatchCandidate;
import io.datapulse.sellerops.persistence.PromoMismatchJdbcRepository;
import io.datapulse.sellerops.persistence.PromoMismatchJdbcRepository.PromoMismatchCandidate;
import io.datapulse.sellerops.persistence.StockMismatchJdbcRepository;
import io.datapulse.sellerops.persistence.StockMismatchJdbcRepository.StockCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MismatchMonitorService {

    private final PriceMismatchJdbcRepository priceMismatchRepository;
    private final PromoMismatchJdbcRepository promoMismatchRepository;
    private final FinanceMismatchJdbcRepository financeMismatchRepository;
    private final StockMismatchJdbcRepository stockMismatchRepository;
    private final GridClickHouseReadRepository chRepository;
    private final GridPostgresReadRepository pgRepository;
    private final MismatchJdbcRepository mismatchJdbcRepository;
    private final MismatchProperties mismatchProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public void checkAllMismatches(long workspaceId) {
        try {
            checkPriceMismatches(workspaceId);
        } catch (Exception e) {
            log.error("Price mismatch check failed: workspaceId={}, error={}",
                    workspaceId, e.getMessage(), e);
        }

        try {
            checkPromoMismatches(workspaceId);
        } catch (Exception e) {
            log.error("Promo mismatch check failed: workspaceId={}, error={}",
                    workspaceId, e.getMessage(), e);
        }

        try {
            checkFinanceMismatches(workspaceId);
        } catch (Exception e) {
            log.error("Finance mismatch check failed: workspaceId={}, error={}",
                    workspaceId, e.getMessage(), e);
        }

        try {
            checkStockMismatches(workspaceId);
        } catch (Exception e) {
            log.error("Stock mismatch check failed: workspaceId={}, error={}",
                    workspaceId, e.getMessage(), e);
        }

        try {
            autoResolveClearedMismatches(workspaceId);
        } catch (Exception e) {
            log.error("Mismatch auto-resolution failed: workspaceId={}, error={}",
                    workspaceId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void checkPriceMismatches(long workspaceId) {
        List<PriceMismatchCandidate> mismatches = priceMismatchRepository.findPriceMismatches(
                workspaceId, mismatchProperties.getPriceWarningThresholdPct());

        for (PriceMismatchCandidate mismatch : mismatches) {
            BigDecimal deltaPct = computeDeltaPct(mismatch.currentPrice(), mismatch.expectedPrice());
            String severity = determinePriceSeverity(deltaPct);

            publishMismatch(
                    mismatch.workspaceId(),
                    mismatch.connectionId(),
                    severity,
                    "Price mismatch: %s (%s)".formatted(mismatch.skuCode(), mismatch.offerName()),
                    buildPriceDetailsJson(mismatch, deltaPct)
            );
        }

        if (!mismatches.isEmpty()) {
            log.info("Price mismatch check completed: workspaceId={}, mismatchesFound={}",
                    workspaceId, mismatches.size());
        }
    }

    @Transactional(readOnly = true)
    public void checkPromoMismatches(long workspaceId) {
        List<PromoMismatchCandidate> mismatches =
                promoMismatchRepository.findPromoMismatches(workspaceId);

        for (PromoMismatchCandidate mismatch : mismatches) {
            publishMismatch(
                    mismatch.workspaceId(),
                    mismatch.connectionId(),
                    MismatchSeverity.WARNING.name(),
                    "Promo mismatch: %s (%s)".formatted(mismatch.skuCode(), mismatch.offerName()),
                    buildPromoDetailsJson(mismatch)
            );
        }

        if (!mismatches.isEmpty()) {
            log.info("Promo mismatch check completed: workspaceId={}, mismatchesFound={}",
                    workspaceId, mismatches.size());
        }
    }

    @Transactional(readOnly = true)
    public void checkFinanceMismatches(long workspaceId) {
        List<FinanceGapCandidate> gaps = financeMismatchRepository.findFinanceGaps(
                workspaceId, mismatchProperties.getFinanceGapHoursThreshold());

        for (FinanceGapCandidate gap : gaps) {
            publishMismatch(
                    gap.workspaceId(),
                    gap.connectionId(),
                    MismatchSeverity.CRITICAL.name(),
                    "Finance gap: %s (%s)".formatted(gap.connectionName(), gap.marketplaceType()),
                    buildFinanceDetailsJson(gap)
            );
        }

        if (!gaps.isEmpty()) {
            log.info("Finance mismatch check completed: workspaceId={}, gapsFound={}",
                    workspaceId, gaps.size());
        }
    }

    public void checkStockMismatches(long workspaceId) {
        List<StockCandidate> pgStocks = stockMismatchRepository.findCanonicalStocks(workspaceId);
        if (pgStocks.isEmpty()) {
            return;
        }

        List<Long> connectionIds = pgRepository.findConnectionIds(workspaceId);
        Map<Long, Integer> chStocks = ChSafeQuery.getOrFallback(
                () -> chRepository.findLatestSnapshotStocks(connectionIds),
                null, "stockMismatch");
        if (chStocks == null) {
            return;
        }

        int found = 0;
        for (StockCandidate pg : pgStocks) {
            Integer chStock = chStocks.get(pg.offerId());
            if (chStock == null) {
                continue;
            }
            int delta = Math.abs(pg.canonicalStock() - chStock);
            double deltaPct = chStock == 0 ? 100.0
                    : (double) delta / chStock * 100;

            boolean thresholdBreached = delta > mismatchProperties.getStockAbsoluteThreshold()
                    || deltaPct > mismatchProperties.getStockPercentThreshold();

            if (thresholdBreached) {
                found++;
                publishMismatch(
                        pg.workspaceId(),
                        pg.connectionId(),
                        MismatchSeverity.WARNING.name(),
                        "Stock mismatch: %s (%s)".formatted(pg.skuCode(), pg.offerName()),
                        buildStockDetailsJson(pg, chStock, delta, deltaPct)
                );
            }
        }

        if (found > 0) {
            log.info("Stock mismatch check completed: workspaceId={}, mismatchesFound={}",
                    workspaceId, found);
        }
    }

    @Transactional
    public void autoResolveClearedMismatches(long workspaceId) {
        int resolved = mismatchJdbcRepository.autoResolveCleared(workspaceId);
        if (resolved > 0) {
            log.info("Auto-resolved cleared mismatches: workspaceId={}, count={}",
                    workspaceId, resolved);
        }
    }

    private void publishMismatch(long workspaceId, long connectionId,
                                  String severity, String title, String details) {
        eventPublisher.publishEvent(new AlertTriggeredEvent(
                workspaceId, connectionId, severity, title, details, false));
    }

    private BigDecimal computeDeltaPct(BigDecimal actual, BigDecimal expected) {
        if (expected.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        return actual.subtract(expected)
                .divide(expected, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .abs();
    }

    private String determinePriceSeverity(BigDecimal deltaPct) {
        if (deltaPct.compareTo(mismatchProperties.getPriceCriticalThresholdPct()) > 0) {
            return MismatchSeverity.CRITICAL.name();
        }
        return MismatchSeverity.WARNING.name();
    }

    private String buildPriceDetailsJson(PriceMismatchCandidate mismatch, BigDecimal deltaPct) {
        return toJson(Map.of(
                "mismatch_type", MismatchType.PRICE.name(),
                "offer_id", mismatch.offerId(),
                "offer_name", mismatch.offerName(),
                "sku_code", mismatch.skuCode(),
                "expected_value", mismatch.expectedPrice().toPlainString(),
                "actual_value", mismatch.currentPrice().toPlainString(),
                "delta_pct", deltaPct.toPlainString()
        ));
    }

    private String buildPromoDetailsJson(PromoMismatchCandidate mismatch) {
        return toJson(Map.of(
                "mismatch_type", MismatchType.PROMO_PARTICIPATION.name(),
                "offer_id", mismatch.offerId(),
                "offer_name", mismatch.offerName(),
                "sku_code", mismatch.skuCode(),
                "expected_value", mismatch.actionOutcome(),
                "actual_value", mismatch.canonicalStatus(),
                "delta_pct", "0"
        ));
    }

    private String buildStockDetailsJson(StockCandidate pg, int chStock,
                                             int delta, double deltaPct) {
        return toJson(Map.of(
                "mismatch_type", MismatchType.STOCK_INCONSISTENCY.name(),
                "offer_id", pg.offerId(),
                "offer_name", pg.offerName(),
                "sku_code", pg.skuCode(),
                "expected_value", String.valueOf(chStock),
                "actual_value", String.valueOf(pg.canonicalStock()),
                "delta_pct", String.valueOf(Math.round(deltaPct * 100) / 100.0)
        ));
    }

    private String buildFinanceDetailsJson(FinanceGapCandidate gap) {
        return toJson(Map.of(
                "mismatch_type", MismatchType.FINANCE_GAP.name(),
                "connection_name", gap.connectionName(),
                "marketplace_type", gap.marketplaceType(),
                "expected_value", "finance data present",
                "actual_value", gap.lastFinanceDate() != null
                        ? "last entry: " + gap.lastFinanceDate() : "no data",
                "delta_pct", "0"
        ));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize mismatch details", e);
            return "{}";
        }
    }
}
