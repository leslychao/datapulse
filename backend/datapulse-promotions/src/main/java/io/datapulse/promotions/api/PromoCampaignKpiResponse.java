package io.datapulse.promotions.api;

public record PromoCampaignKpiResponse(
    long activeCount,
    long upcomingCount,
    long productsParticipating) {}
