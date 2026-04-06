package io.datapulse.pricing.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.pricing.domain.PolicySnapshot;
import io.datapulse.pricing.domain.PolicyType;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.StrategyResult;
import io.datapulse.pricing.domain.VelocityAdaptiveParams;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VelocityAdaptiveStrategy implements PricingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public PolicyType strategyType() {
        return PolicyType.VELOCITY_ADAPTIVE;
    }

    @Override
    public StrategyResult calculate(PricingSignalSet signals, PolicySnapshot policy) {
        VelocityAdaptiveParams params = parseParams(policy.strategyParams());

        if (signals.currentPrice() == null
                || signals.currentPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyResult.skip("Current price not available",
                    MessageCodes.PRICING_CURRENT_PRICE_MISSING);
        }

        if (signals.salesVelocityLong() == null
                || signals.salesVelocityLong().compareTo(BigDecimal.ZERO) <= 0) {
            return StrategyResult.skip("Insufficient sales data for velocity analysis",
                    MessageCodes.PRICING_VELOCITY_INSUFFICIENT_DATA);
        }

        BigDecimal baselineUnits = signals.salesVelocityLong()
                .multiply(BigDecimal.valueOf(params.effectiveVelocityWindowLongDays()));
        if (baselineUnits.compareTo(
                BigDecimal.valueOf(params.effectiveMinBaselineSales())) < 0) {
            return StrategyResult.skip(
                    "Baseline sales %.1f < min %d"
                            .formatted(baselineUnits.doubleValue(),
                                    params.effectiveMinBaselineSales()),
                    MessageCodes.PRICING_VELOCITY_INSUFFICIENT_DATA);
        }

        BigDecimal velocityShort = signals.salesVelocityShort() != null
                ? signals.salesVelocityShort() : BigDecimal.ZERO;

        BigDecimal velocityRatio = velocityShort
                .divide(signals.salesVelocityLong(), 4, RoundingMode.HALF_UP);

        BigDecimal decelThreshold = params.effectiveDecelerationThreshold();
        BigDecimal accelThreshold = params.effectiveAccelerationThreshold();

        BigDecimal adjustment;

        if (velocityRatio.compareTo(decelThreshold) < 0) {
            BigDecimal deviation = decelThreshold.subtract(velocityRatio)
                    .divide(decelThreshold, 4, RoundingMode.HALF_UP);
            BigDecimal cappedDeviation = deviation.min(BigDecimal.ONE);
            adjustment = params.effectiveDecelerationDiscountPct()
                    .negate()
                    .multiply(cappedDeviation);
        } else if (velocityRatio.compareTo(accelThreshold) > 0) {
            BigDecimal deviation = velocityRatio.subtract(accelThreshold)
                    .divide(accelThreshold, 4, RoundingMode.HALF_UP);
            BigDecimal cappedDeviation = deviation.min(BigDecimal.ONE);
            adjustment = params.effectiveAccelerationMarkupPct()
                    .multiply(cappedDeviation);
        } else {
            return StrategyResult.skip(
                    buildStableExplanation(velocityShort, signals.salesVelocityLong(),
                            velocityRatio, params),
                    MessageCodes.PRICING_VELOCITY_STABLE);
        }

        BigDecimal rawTargetPrice = signals.currentPrice()
                .multiply(BigDecimal.ONE.add(adjustment))
                .setScale(2, RoundingMode.HALF_UP);

        String explanation = buildExplanation(
                velocityShort, signals.salesVelocityLong(),
                velocityRatio, adjustment, rawTargetPrice, params);

        return StrategyResult.success(rawTargetPrice, explanation);
    }

    private String buildExplanation(
            BigDecimal velocityShort, BigDecimal velocityLong,
            BigDecimal ratio, BigDecimal adjustment,
            BigDecimal rawPrice, VelocityAdaptiveParams params) {
        return ("velocity_short=%s u/d (%dd), velocity_long=%s u/d (%dd), "
                + "ratio=%s, threshold_decel=%s, threshold_accel=%s, "
                + "adjustment=%+.1f%%, raw=%s")
                .formatted(
                        velocityShort.toPlainString(),
                        params.effectiveVelocityWindowShortDays(),
                        velocityLong.toPlainString(),
                        params.effectiveVelocityWindowLongDays(),
                        ratio.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        params.effectiveDecelerationThreshold().toPlainString(),
                        params.effectiveAccelerationThreshold().toPlainString(),
                        adjustment.multiply(BigDecimal.valueOf(100)).doubleValue(),
                        rawPrice.toPlainString());
    }

    private String buildStableExplanation(
            BigDecimal velocityShort, BigDecimal velocityLong,
            BigDecimal ratio, VelocityAdaptiveParams params) {
        return ("velocity_short=%s u/d (%dd), velocity_long=%s u/d (%dd), "
                + "ratio=%s — within [%s, %s], stable")
                .formatted(
                        velocityShort.toPlainString(),
                        params.effectiveVelocityWindowShortDays(),
                        velocityLong.toPlainString(),
                        params.effectiveVelocityWindowLongDays(),
                        ratio.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        params.effectiveDecelerationThreshold().toPlainString(),
                        params.effectiveAccelerationThreshold().toPlainString());
    }

    private VelocityAdaptiveParams parseParams(String json) {
        try {
            return objectMapper.readValue(json, VelocityAdaptiveParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Invalid VELOCITY_ADAPTIVE strategy_params JSON", e);
        }
    }
}
