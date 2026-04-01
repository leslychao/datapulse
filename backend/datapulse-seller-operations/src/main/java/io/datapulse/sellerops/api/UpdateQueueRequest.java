package io.datapulse.sellerops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateQueueRequest(
    @NotBlank @Size(max = 200) String name,
    Map<String, Object> autoCriteria,
    boolean enabled
) {
}
