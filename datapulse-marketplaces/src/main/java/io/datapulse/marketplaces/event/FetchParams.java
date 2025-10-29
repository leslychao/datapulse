package io.datapulse.marketplaces.event;

public record FetchParams(
    String warehouseId,
    String postingStatus,
    Integer pageSize,
    String cursor,
    String sku,
    String region,
    String campaignId,
    String sellerId,
    Boolean includeReturns,
    Boolean includeFbo,
    String granularity
) {

  public static FetchParams empty() {
    return new FetchParams(null, null, null, null, null, null, null, null, null, null, null);
  }
}