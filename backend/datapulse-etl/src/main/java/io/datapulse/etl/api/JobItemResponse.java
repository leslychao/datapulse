package io.datapulse.etl.api;

import java.time.OffsetDateTime;

public record JobItemResponse(
        long id,
        String sourceId,
        int pageNumber,
        String s3Key,
        String status,
        Integer recordCount,
        long byteSize,
        OffsetDateTime capturedAt,
        OffsetDateTime processedAt
) {
}
