package io.datapulse.etl.api;

import java.util.List;

public record BulkImportResponse(
        int imported,
        int skipped,
        List<BulkImportError> errors
) {

    public record BulkImportError(
            int row,
            String field,
            String message
    ) {
    }
}
