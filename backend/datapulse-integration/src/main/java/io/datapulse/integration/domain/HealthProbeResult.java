package io.datapulse.integration.domain;

public record HealthProbeResult(boolean success, String errorCode, String externalAccountId) {

    public static HealthProbeResult success(String externalAccountId) {
        return new HealthProbeResult(true, null, externalAccountId);
    }

    public static HealthProbeResult failure(String errorCode) {
        return new HealthProbeResult(false, errorCode, null);
    }
}
