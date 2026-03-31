package io.datapulse.common.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String messageKey,
        String path,
        List<FieldError> fieldErrors
) {

    public record FieldError(
            String field,
            String messageKey,
            String rejectedValue
    ) {
    }
}
