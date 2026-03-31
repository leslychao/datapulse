package io.datapulse.integration.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.datapulse.integration.domain.MarketplaceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateConnectionRequest(
        @NotNull MarketplaceType marketplaceType,
        @NotBlank String name,
        @NotNull JsonNode credentials
) {
}
