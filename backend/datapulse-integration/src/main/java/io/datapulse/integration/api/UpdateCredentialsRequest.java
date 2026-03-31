package io.datapulse.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UpdateCredentialsRequest(
        @NotNull JsonNode credentials
) {
}
