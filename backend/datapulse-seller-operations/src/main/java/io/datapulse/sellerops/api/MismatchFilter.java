package io.datapulse.sellerops.api;

public record MismatchFilter(
    String type,
    Long connectionId,
    String severity
) {
}
