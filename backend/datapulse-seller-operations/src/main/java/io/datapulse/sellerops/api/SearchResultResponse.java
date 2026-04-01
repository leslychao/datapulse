package io.datapulse.sellerops.api;

import java.util.List;

public record SearchResultResponse(
    List<ProductResult> products,
    List<PolicyResult> policies,
    List<PromoResult> promos,
    List<ViewResult> views) {

  public record ProductResult(
      long offerId,
      String skuCode,
      String productName,
      String marketplaceType) {}

  public record PolicyResult(long policyId, String name) {}

  public record PromoResult(long campaignId, String name) {}

  public record ViewResult(long viewId, String name) {}
}
