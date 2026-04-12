package io.datapulse.bidding.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkUnlockRequest(
    @NotEmpty List<Long> lockIds
) {
}
