package io.datapulse.marketplaces.adapter;

import java.util.List;

public final class OzonMetrics {

  private OzonMetrics() {
  }

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
