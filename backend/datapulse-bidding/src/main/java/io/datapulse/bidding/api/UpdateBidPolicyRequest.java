package io.datapulse.bidding.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateBidPolicyRequest(
    @NotBlank String name,
    @NotBlank String executionMode,
    @NotNull JsonNode config
) {
}
