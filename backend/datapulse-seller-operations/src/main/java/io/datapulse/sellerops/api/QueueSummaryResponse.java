package io.datapulse.sellerops.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record QueueSummaryResponse(
        long queueId,
        String name,
        String queueType,
        long pendingCount,
        long inProgressCount,
        long totalActiveCount,
        @JsonProperty("isSystem") boolean system,
        Map<String, Object> autoCriteria
) {
}
