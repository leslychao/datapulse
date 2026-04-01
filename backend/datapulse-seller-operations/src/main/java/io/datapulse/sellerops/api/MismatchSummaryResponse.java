package io.datapulse.sellerops.api;

public record MismatchSummaryResponse(long total, long open, long acknowledged, long resolved) {
}
