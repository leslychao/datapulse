package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
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
    super(
        MarketplaceType.WILDBERRIES,
        downloader,
        headerProvider,
        resolver,
        marketplaceProperties
    );
  }

  public Snapshot<WbWarehouseListRaw> downloadWarehouseList(long accountId) {
    return doGet(
        accountId,
        EndpointKey.DICT_WB_WAREHOUSES,
        WbWarehouseListRaw.class
    );
  }
}
