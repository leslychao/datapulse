package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.dto.raw.ozon.OzonAnalyticsApiRaw;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class OzonAdapter extends AbstractMarketplaceAdapter {

  public OzonAdapter(
      EndpointsResolver resolver,
      MarketplaceStreamingDownloadService downloader,
      HttpHeaderProvider headerProvider,
      MarketplaceProperties marketplaceProperties
  ) {
    super(downloader, headerProvider, resolver, marketplaceProperties);
  }

  @Override
  protected MarketplaceType marketplaceType() {
    return MarketplaceType.OZON;
  }

  @Override
  public Snapshot<OzonAnalyticsApiRaw> downloadSalesSnapshot(
      long accountId,
      LocalDate from,
      LocalDate to
  ) {
    Map<String, Object> body = Map.of(
        "date_from", from.toString(),
        "date_to", to.toString(),
        "dimension", OzonAnalyticsSchema.SALES_FACT_DIMENSIONS,
        "metrics", OzonAnalyticsSchema.SALES_FACT_METRICS,
        "limit", 1000L,
        "filters", java.util.List.of(),
        "sort", java.util.List.of()
    );
    return doPost(accountId, EndpointKey.SALES, body, OzonAnalyticsApiRaw.class);
  }
}
