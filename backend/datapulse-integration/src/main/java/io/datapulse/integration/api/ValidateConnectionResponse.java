package io.datapulse.integration.api;

public record ValidateConnectionResponse(
        boolean valid,
        String errorCode
) {
}
