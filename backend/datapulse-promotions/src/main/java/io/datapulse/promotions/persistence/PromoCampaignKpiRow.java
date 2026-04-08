package io.datapulse.promotions.persistence;

public record PromoCampaignKpiRow(
    long activeCount,
    long upcomingCount,
    long productsParticipating) {}
