package io.datapulse.etl.api;

import java.util.List;

public record BulkFormulaCostResponse(
    int updated,
    int skipped,
    List<String> errors
) {
}
