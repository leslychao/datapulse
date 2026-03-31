package io.datapulse.etl.api;

public record CostProfileFilter(
        Long sellerSkuId,
        String search
) {
}
