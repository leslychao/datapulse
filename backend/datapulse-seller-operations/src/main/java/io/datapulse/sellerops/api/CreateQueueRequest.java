package io.datapulse.sellerops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateQueueRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull String queueType,
        Map<String, Object> autoCriteria
) {
}
