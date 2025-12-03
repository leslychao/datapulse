package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
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
    super(
        MarketplaceType.OZON,
        downloader,
        headerProvider,
        resolver,
        marketplaceProperties
    );
  }

  public Snapshot<OzonWarehouseFbsListRaw> downloadFbsWarehouses(long accountId) {
    Map<String, Object> body = Map.of("limit", 100);
    return doPost(
        accountId,
        EndpointKey.DICT_OZON_WAREHOUSES_FBS,
        body,
        OzonWarehouseFbsListRaw.class
    );
  }

  public Snapshot<OzonClusterListRaw> downloadFboWarehouses(long accountId) {
    Map<String, Object> body = Map.of("cluster_type", "CLUSTER_TYPE_OZON");
    return doPost(
        accountId,
        EndpointKey.DICT_OZON_CLUSTERS,
        body,
        OzonClusterListRaw.class
    );
  }
}
