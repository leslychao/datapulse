package io.datapulse.sellerops.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.audit.domain.event.AlertTriggeredEvent;
import io.datapulse.sellerops.config.MismatchProperties;
import io.datapulse.sellerops.persistence.PriceMismatchJdbcRepository;
import io.datapulse.sellerops.persistence.PriceMismatchJdbcRepository.PriceMismatchCandidate;
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

    private final PriceMismatchJdbcRepository mismatchRepository;
    private final MismatchProperties mismatchProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void checkPriceMismatches(long workspaceId) {
        List<PriceMismatchCandidate> mismatches = mismatchRepository.findPriceMismatches(
                workspaceId, mismatchProperties.getPriceWarningThresholdPct());

        for (PriceMismatchCandidate mismatch : mismatches) {
            BigDecimal deltaPct = computeDeltaPct(mismatch.currentPrice(), mismatch.expectedPrice());
            String severity = determineSeverity(deltaPct);

            String details = buildDetailsJson(mismatch, deltaPct);
            String title = "Price mismatch: %s (%s)".formatted(
                    mismatch.skuCode(), mismatch.offerName());

            eventPublisher.publishEvent(new AlertTriggeredEvent(
                    mismatch.workspaceId(),
                    mismatch.connectionId(),
                    severity,
                    title,
                    details,
                    false
            ));
        }

        if (!mismatches.isEmpty()) {
            log.info("Price mismatch check completed: workspaceId={}, mismatchesFound={}",
                    workspaceId, mismatches.size());
        }
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

    private String determineSeverity(BigDecimal deltaPct) {
        if (deltaPct.compareTo(mismatchProperties.getPriceCriticalThresholdPct()) > 0) {
            return MismatchSeverity.CRITICAL.name();
        }
        return MismatchSeverity.WARNING.name();
    }

    private String buildDetailsJson(PriceMismatchCandidate mismatch, BigDecimal deltaPct) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "mismatch_type", MismatchType.PRICE.name(),
                    "offer_id", mismatch.offerId(),
                    "offer_name", mismatch.offerName(),
                    "sku_code", mismatch.skuCode(),
                    "expected_value", mismatch.expectedPrice().toPlainString(),
                    "actual_value", mismatch.currentPrice().toPlainString(),
                    "delta_pct", deltaPct.toPlainString()
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize mismatch details: offerId={}", mismatch.offerId(), e);
            return "{}";
        }
    }
}
