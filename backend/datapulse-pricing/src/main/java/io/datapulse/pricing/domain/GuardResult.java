package io.datapulse.pricing.domain;

import java.util.Map;

/**
 * Result of a single guard check.
 *
 * @param passed    whether the guard allowed the price change
 * @param guardName technical name of the guard (e.g. "stale_data_guard")
 * @param reason    i18n message key (e.g. "pricing.guard.stale_data.stale"), null if passed
 * @param args      interpolation parameters for the message key (nullable)
 */
public record GuardResult(
        boolean passed,
        String guardName,
        String reason,
        Map<String, Object> args
) {

    public static GuardResult pass(String guardName) {
        return new GuardResult(true, guardName, null, null);
    }

    public static GuardResult block(String guardName, String reason) {
        return new GuardResult(false, guardName, reason, null);
    }

    public static GuardResult block(String guardName, String reason, Map<String, Object> args) {
        return new GuardResult(false, guardName, reason, args);
    }
}
