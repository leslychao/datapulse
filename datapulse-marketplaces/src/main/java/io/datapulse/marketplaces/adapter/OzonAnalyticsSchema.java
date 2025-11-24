package io.datapulse.marketplaces.adapter;

import java.util.List;

public final class OzonAnalyticsSchema {

  private OzonAnalyticsSchema() {
  }

  public static final List<String> SALES_FACT_DIMENSIONS = List.of(
      "day",
      "sku",
      "offer_id",
      "warehouse_id"
  );

  public static final List<String> SALES_FACT_METRICS = List.of(
      "revenue",
      "ordered_units",
      "delivered_units",
      "returns",
      "cancellations",
      "hits_view_search",
      "hits_view_pdp",
      "hits_tocart",
      "session_view"
  );
}
