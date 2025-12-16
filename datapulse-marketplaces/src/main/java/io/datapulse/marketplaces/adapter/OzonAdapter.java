package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.OzonCategoryTreeRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductListItemRaw;
import io.datapulse.marketplaces.dto.raw.tariff.OzonProductInfoPricesItemRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.json.OzonCursorExtractor;
import io.datapulse.marketplaces.json.OzonLastIdExtractor;
import io.datapulse.marketplaces.service.AuthAccountIdResolver;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class OzonAdapter extends AbstractMarketplaceAdapter {

  private final CursorLimitPartitionKeyGenerator partitionKeyGenerator;

  public OzonAdapter(
      EndpointsResolver resolver,
      MarketplaceStreamingDownloadService downloader,
      HttpHeaderProvider headerProvider,
      MarketplaceProperties marketplaceProperties,
      AuthAccountIdResolver authAccountIdResolver,
      CursorLimitPartitionKeyGenerator partitionKeyGenerator
  ) {
    super(
        MarketplaceType.OZON,
        downloader,
        headerProvider,
        resolver,
        marketplaceProperties,
        authAccountIdResolver
    );
    this.partitionKeyGenerator = partitionKeyGenerator;
  }

  public Snapshot<OzonWarehouseFbsListRaw> downloadFbsWarehouses(long accountId) {
    return doPost(
        accountId,
        EndpointKey.DICT_OZON_WAREHOUSES_FBS,
        Map.of("limit", 100),
        OzonWarehouseFbsListRaw.class
    );
  }

  public Snapshot<OzonClusterListRaw> downloadFboWarehouses(long accountId) {
    return doPost(
        accountId,
        EndpointKey.DICT_OZON_CLUSTERS,
        Map.of("cluster_type", "CLUSTER_TYPE_OZON"),
        OzonClusterListRaw.class
    );
  }

  public Snapshot<OzonCategoryTreeRaw> downloadCategoryTree(long accountId) {
    return doPost(
        accountId,
        EndpointKey.DICT_OZON_CATEGORY_TREE,
        Map.of("language", "DEFAULT"),
        OzonCategoryTreeRaw.class
    );
  }

  public Snapshot<OzonProductInfoPricesItemRaw> downloadProductInfoPricesPage(
      long accountId,
      String cursor,
      int limit
  ) {
    String effectiveCursor = cursor == null ? "" : cursor;

    Map<String, Object> body = Map.of(
        "cursor", effectiveCursor,
        "filter", Map.of("visibility", "ALL"),
        "limit", limit
    );

    String partitionKey = partitionKeyGenerator.generate(effectiveCursor, limit);

    Snapshot<OzonProductInfoPricesItemRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.DICT_OZON_PRODUCT_INFO_PRICES,
        body,
        partitionKey,
        OzonProductInfoPricesItemRaw.class
    );

    String nextCursor = OzonCursorExtractor.extractCursor(downloaded.file());
    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextCursor);
  }

  public Snapshot<OzonProductListItemRaw> downloadProductsPage(
      long accountId,
      String lastId,
      int limit
  ) {
    String effectiveLastId = lastId == null ? "" : lastId;

    Map<String, Object> body = Map.of(
        "filter", Map.of("visibility", "ALL"),
        "last_id", effectiveLastId,
        "limit", limit
    );

    String partitionKey = partitionKeyGenerator.generate(effectiveLastId, limit);

    Snapshot<OzonProductListItemRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.DICT_OZON_PRODUCTS,
        body,
        partitionKey,
        OzonProductListItemRaw.class
    );

    String nextLastId = OzonLastIdExtractor.extractLastId(downloaded.file());
    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextLastId);
  }
}
