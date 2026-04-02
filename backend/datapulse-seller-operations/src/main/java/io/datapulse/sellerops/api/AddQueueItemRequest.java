package io.datapulse.sellerops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddQueueItemRequest(
        @NotBlank String entityType,
        @NotNull Long entityId,
        String note
) {
}
