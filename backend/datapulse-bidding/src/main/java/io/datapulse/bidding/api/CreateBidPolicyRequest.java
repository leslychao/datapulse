package io.datapulse.bidding.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBidPolicyRequest(
    @NotBlank String name,
    @NotBlank String strategyType,
    @NotBlank String executionMode,
    @NotNull JsonNode config
) {
}
