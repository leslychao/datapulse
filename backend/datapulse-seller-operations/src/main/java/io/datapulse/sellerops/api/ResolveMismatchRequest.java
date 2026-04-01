package io.datapulse.sellerops.api;

import jakarta.validation.constraints.NotBlank;

public record ResolveMismatchRequest(
    @NotBlank String resolution,
    String note
) {
}
