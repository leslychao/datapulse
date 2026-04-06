package io.datapulse.pricing.api;

import java.util.List;

public record BulkCompetitorUploadResponse(
        int totalRows,
        int created,
        int skipped,
        List<String> errors
) {
}
