package io.datapulse.etl.domain;

import java.util.List;

public record SubSourceResult(
        String sourceId,
        EventResultStatus status,
        String lastCursor,
        int pagesProcessed,
        int recordsProcessed,
        int recordsSkipped,
        List<String> errors
) {

    public boolean isSuccess() {
        return status == EventResultStatus.COMPLETED || status == EventResultStatus.COMPLETED_WITH_ERRORS;
    }

    public static SubSourceResult success(String sourceId, int pagesProcessed, int recordsProcessed) {
        return new SubSourceResult(sourceId, EventResultStatus.COMPLETED,
                null, pagesProcessed, recordsProcessed, 0, List.of());
    }

    public static SubSourceResult failed(String sourceId, String error) {
        return failed(sourceId, error, null);
    }

    public static SubSourceResult failed(String sourceId, String error, String lastCursor) {
        return new SubSourceResult(sourceId, EventResultStatus.FAILED,
                lastCursor, 0, 0, 0, List.of(error));
    }

    public static SubSourceResult partial(String sourceId, String lastCursor,
                                          int pagesProcessed, int recordsProcessed,
                                          int recordsSkipped, List<String> errors) {
        return new SubSourceResult(sourceId, EventResultStatus.COMPLETED_WITH_ERRORS,
                lastCursor, pagesProcessed, recordsProcessed, recordsSkipped, errors);
    }
}
