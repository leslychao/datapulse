package io.datapulse.integration.domain;

import java.util.Map;

public record HealthProbeResult(
    boolean success,
    String errorCode,
    String externalAccountId,
    Map<String, Object> metadata) {

    public static HealthProbeResult success(String externalAccountId) {
        return new HealthProbeResult(true, null, externalAccountId, null);
    }

    public static HealthProbeResult success(String externalAccountId, Map<String, Object> metadata) {
        return new HealthProbeResult(true, null, externalAccountId, metadata);
    }

    public static HealthProbeResult failure(String errorCode) {
        return new HealthProbeResult(false, errorCode, null, null);
    }
}
