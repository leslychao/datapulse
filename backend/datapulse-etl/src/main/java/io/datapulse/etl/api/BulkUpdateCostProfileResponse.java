package io.datapulse.etl.api;

public record BulkUpdateCostProfileResponse(
    int updatedCount,
    int createdCount,
    int errorCount
) {
}
