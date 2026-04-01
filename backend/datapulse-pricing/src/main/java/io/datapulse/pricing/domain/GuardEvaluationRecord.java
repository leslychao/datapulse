package io.datapulse.pricing.domain;

import java.util.Map;

/**
 * Serialized into guards_evaluated JSONB in price_decision.
 *
 * @param name       guard name
 * @param passed     whether it passed
 * @param messageKey i18n message key (null if passed)
 * @param args       interpolation parameters for frontend translation (nullable)
 */
public record GuardEvaluationRecord(
        String name,
        boolean passed,
        String messageKey,
        Map<String, Object> args
) {
}
