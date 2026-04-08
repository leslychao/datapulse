package io.datapulse.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datapulse.analytics")
public record AnalyticsQueryProperties(
        InventoryProperties inventory,
        DataQualityProperties dataQuality
) {

    public AnalyticsQueryProperties {
        if (inventory == null) {
            inventory = new InventoryProperties(14, 30, 7);
        }
        if (dataQuality == null) {
            dataQuality = new DataQualityProperties(24, 48, 2, 6, 3, 30, 0.05, 30);
        }
    }

    public record InventoryProperties(
            int velocityWindowDays,
            int targetDaysOfCover,
            int leadTimeDays
    ) {}

    public record DataQualityProperties(
            int staleFinanceThresholdHours,
            int staleStateThresholdHours,
            int residualAnomalyStdMultiplier,
            int residualMinSampleSize,
            int spikeMedianMultiplier,
            int spikeLookbackDays,
            double skuAttributionRateThreshold,
            int calibrationPeriodDays
    ) {}
}
