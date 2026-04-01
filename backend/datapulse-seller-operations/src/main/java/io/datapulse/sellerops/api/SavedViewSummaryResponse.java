package io.datapulse.sellerops.api;

import java.time.OffsetDateTime;

public record SavedViewSummaryResponse(
        long viewId,
        String name,
        boolean isDefault,
        boolean isSystem,
        OffsetDateTime createdAt
) {
}
