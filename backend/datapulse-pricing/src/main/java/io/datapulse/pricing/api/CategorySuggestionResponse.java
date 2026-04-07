package io.datapulse.pricing.api;

public record CategorySuggestionResponse(
    Long id,
    String name,
    String externalCategoryId
) {
}
