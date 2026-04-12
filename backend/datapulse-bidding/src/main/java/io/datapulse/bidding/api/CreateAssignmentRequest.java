package io.datapulse.bidding.api;

import jakarta.validation.constraints.NotBlank;

public record CreateAssignmentRequest(
    Long marketplaceOfferId,
    String campaignExternalId,
    Long categoryId,
    @NotBlank String scope
) {
}
