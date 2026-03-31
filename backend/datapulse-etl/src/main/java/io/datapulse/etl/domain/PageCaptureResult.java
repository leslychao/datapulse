package io.datapulse.etl.domain;

public record PageCaptureResult(
        CaptureResult captureResult,
        String cursor
) {}
