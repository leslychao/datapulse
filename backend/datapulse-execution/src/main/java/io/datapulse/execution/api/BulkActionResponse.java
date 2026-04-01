package io.datapulse.execution.api;

import java.util.List;

public record BulkActionResponse(
    int processed,
    int skipped,
    int errored,
    List<String> errors
) {
}
