package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import org.springframework.stereotype.Component;

@Component
public final class WbAdapter extends AbstractMarketplaceAdapter {

  public WbAdapter(
      EndpointsResolver resolver,
      MarketplaceStreamingDownloadService downloader,
      HttpHeaderProvider headerProvider,
      MarketplaceProperties marketplaceProperties
  ) {
    super(downloader, headerProvider, resolver, marketplaceProperties);
  }

  @Override
  protected MarketplaceType marketplaceType() {
    return MarketplaceType.WILDBERRIES;
  }

}
