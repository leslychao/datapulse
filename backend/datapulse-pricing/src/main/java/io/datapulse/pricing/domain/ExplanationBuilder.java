package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * Builds human-readable explanation_summary for price decisions.
 * Format follows the documented template with [Section] labels.
 */
@Service
public class ExplanationBuilder {

    private static final DecimalFormat PRICE_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("ru"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        PRICE_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    public String buildChange(BigDecimal currentPrice, BigDecimal targetPrice,
                              PolicySnapshot policy, String strategyExplanation,
                              List<ConstraintRecord> constraints,
                              ExecutionMode executionMode, String actionStatus) {
        var sb = new StringBuilder();

        BigDecimal changePct = computeChangePct(currentPrice, targetPrice);
        String sign = changePct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "−";
        BigDecimal absPct = changePct.abs();

        sb.append("[Решение] CHANGE: %s → %s (%s%.1f%%)\n".formatted(
                formatPrice(currentPrice), formatPrice(targetPrice), sign, absPct.doubleValue()));

        sb.append("[Политика] «%s» (%s, v%d)\n".formatted(
                policy.name(), policy.strategyType(), policy.version()));

        sb.append("[Стратегия] %s\n".formatted(strategyExplanation));

        if (!constraints.isEmpty()) {
            sb.append("[Ограничения] ");
            for (int i = 0; i < constraints.size(); i++) {
                ConstraintRecord c = constraints.get(i);
                sb.append("%s: %s → %s".formatted(
                        c.name(), formatPrice(c.fromPrice()), formatPrice(c.toPrice())));
                if (i < constraints.size() - 1) {
                    sb.append("; ");
                }
            }
            sb.append("\n");
        }

        sb.append("[Guards] Все пройдены\n");

        sb.append("[Режим] %s → action %s".formatted(executionMode, actionStatus));

        return sb.toString();
    }

    public String buildSkip(String reason, String guardName, String guardDetails) {
        var sb = new StringBuilder();
        sb.append("[Решение] SKIP\n");
        sb.append("[Причина] %s\n".formatted(reason));
        if (guardName != null) {
            sb.append("[Guard] %s: %s".formatted(guardName, guardDetails));
        }
        return sb.toString();
    }

    public String buildHold(String reason, PolicySnapshot policy) {
        var sb = new StringBuilder();
        sb.append("[Решение] HOLD\n");
        sb.append("[Причина] %s\n".formatted(reason));
        if (policy != null) {
            sb.append("[Политика] «%s» (%s, v%d)".formatted(
                    policy.name(), policy.strategyType(), policy.version()));
        }
        return sb.toString();
    }

    public String buildSkipGuard(PolicySnapshot policy, GuardResult blockingGuard) {
        var sb = new StringBuilder();
        sb.append("[Решение] SKIP\n");
        sb.append("[Причина] %s\n".formatted(blockingGuard.reason()));
        if (policy != null) {
            sb.append("[Политика] «%s» (%s, v%d)\n".formatted(
                    policy.name(), policy.strategyType(), policy.version()));
        }
        sb.append("[Guard] %s: %s".formatted(blockingGuard.guardName(), blockingGuard.reason()));
        return sb.toString();
    }

    private BigDecimal computeChangePct(BigDecimal current, BigDecimal target) {
        if (current == null || current.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return target.subtract(current)
                .divide(current, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "—";
        }
        return PRICE_FORMAT.format(price);
    }
}
