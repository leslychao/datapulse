package io.datapulse.etl.domain;

public record CaptureResult(
        long jobItemId,
        String s3Key,
        String contentSha256,
        long byteSize
) {}
