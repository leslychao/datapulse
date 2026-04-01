package io.datapulse.promotions.api;

import java.util.List;

public record BulkPromoActionResponse(
        List<Long> succeeded,
        List<FailedItem> failed
) {
    public record FailedItem(Long actionId, String reason) {
    }
}
