package io.datapulse.analytics.api;

public record ProvenanceRawResponse(
        String presignedUrl,
        String s3Key,
        long byteSize
) {}
