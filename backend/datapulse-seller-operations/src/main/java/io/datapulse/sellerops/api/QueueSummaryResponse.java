package io.datapulse.sellerops.api;

public record QueueSummaryResponse(
        long queueId,
        String name,
        String queueType,
        long pendingCount,
        long inProgressCount,
        long totalActiveCount
) {
}
