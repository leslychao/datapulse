package io.datapulse.sellerops.api;

import java.util.List;

public record MatchingIdsResponse(
        List<Long> offerIds,
        long totalCount,
        boolean truncated
) {
}
