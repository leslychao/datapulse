package io.datapulse.pricing.api;

public record OfferSuggestionResponse(
    Long id,
    String name,
    String marketplaceSku,
    String sellerSku
) {
}
