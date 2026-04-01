package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Applies constraints in documented order:
 * min_price → max_price → max_price_change → min_margin → marketplace_min → rounding.
 * <p>
 * Constraints don't block — they clamp the price. Each applied constraint is recorded.
 */
@Service
@RequiredArgsConstructor
public class PricingConstraintResolver {

    private final ObjectMapper objectMapper;

    public ConstraintResolution resolve(BigDecimal rawPrice, PricingSignalSet signals,
                                        PolicySnapshot policy) {
        List<ConstraintRecord> applied = new ArrayList<>();
        BigDecimal price = rawPrice;

        price = applyMinPrice(price, policy.minPrice(), applied);
        price = applyMaxPrice(price, policy.maxPrice(), applied);
        price = applyMaxPriceChange(price, signals.currentPrice(), policy.maxPriceChangePct(), applied);
        price = applyMinMargin(price, signals.cogs(), policy.minMarginPct(), applied);
        price = applyRounding(price, policy, applied);

        return new ConstraintResolution(price, applied);
    }

    private BigDecimal applyMinPrice(BigDecimal price, BigDecimal minPrice,
                                     List<ConstraintRecord> applied) {
        if (minPrice != null && price.compareTo(minPrice) < 0) {
            applied.add(new ConstraintRecord("min_price", price, minPrice));
            return minPrice;
        }
        return price;
    }

    private BigDecimal applyMaxPrice(BigDecimal price, BigDecimal maxPrice,
                                     List<ConstraintRecord> applied) {
        if (maxPrice != null && price.compareTo(maxPrice) > 0) {
            applied.add(new ConstraintRecord("max_price", price, maxPrice));
            return maxPrice;
        }
        return price;
    }

    private BigDecimal applyMaxPriceChange(BigDecimal price, BigDecimal currentPrice,
                                           BigDecimal maxChangePct,
                                           List<ConstraintRecord> applied) {
        if (maxChangePct == null || currentPrice == null
                || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }

        BigDecimal maxDelta = currentPrice.multiply(maxChangePct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal floor = currentPrice.subtract(maxDelta);
        BigDecimal ceiling = currentPrice.add(maxDelta);

        if (price.compareTo(floor) < 0) {
            applied.add(new ConstraintRecord("max_price_change", price, floor));
            return floor;
        }
        if (price.compareTo(ceiling) > 0) {
            applied.add(new ConstraintRecord("max_price_change", price, ceiling));
            return ceiling;
        }
        return price;
    }

    private BigDecimal applyMinMargin(BigDecimal price, BigDecimal cogs, BigDecimal minMarginPct,
                                      List<ConstraintRecord> applied) {
        if (minMarginPct == null || cogs == null || cogs.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }

        BigDecimal denominator = BigDecimal.ONE.subtract(minMarginPct);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return price;
        }

        BigDecimal marginFloor = cogs.divide(denominator, 2, RoundingMode.HALF_UP);
        if (price.compareTo(marginFloor) < 0) {
            applied.add(new ConstraintRecord("min_margin", price, marginFloor));
            return marginFloor;
        }
        return price;
    }

    private BigDecimal applyRounding(BigDecimal price, PolicySnapshot policy,
                                     List<ConstraintRecord> applied) {
        TargetMarginParams params = tryParseTargetMarginParams(policy);
        if (params == null) {
            return price;
        }

        BigDecimal step = params.effectiveRoundingStep();
        TargetMarginParams.RoundingDirection direction = params.effectiveRoundingDirection();

        BigDecimal rounded = switch (direction) {
            case FLOOR -> price.divide(step, 0, RoundingMode.FLOOR).multiply(step);
            case CEIL -> price.divide(step, 0, RoundingMode.CEILING).multiply(step);
            case NEAREST -> price.divide(step, 0, RoundingMode.HALF_UP).multiply(step);
        };

        if (rounded.compareTo(price) != 0) {
            applied.add(new ConstraintRecord("rounding", price, rounded));
        }
        return rounded;
    }

    private TargetMarginParams tryParseTargetMarginParams(PolicySnapshot policy) {
        if (policy.strategyType() != PolicyType.TARGET_MARGIN) {
            return null;
        }
        try {
            return objectMapper.readValue(policy.strategyParams(), TargetMarginParams.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public record ConstraintResolution(
            BigDecimal clampedPrice,
            List<ConstraintRecord> applied
    ) {
    }
}
