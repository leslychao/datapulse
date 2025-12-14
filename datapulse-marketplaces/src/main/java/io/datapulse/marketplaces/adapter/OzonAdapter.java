package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.OzonCategoryTreeRaw;
import io.datapulse.marketplaces.dto.raw.tariff.OzonProductInfoPricesItemRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.json.SnapshotCursorExtractor;
import io.datapulse.marketplaces.service.AuthAccountIdResolver;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class OzonAdapter extends AbstractMarketplaceAdapter {

  public OzonAdapter(
      EndpointsResolver resolver,
      MarketplaceStreamingDownloadService downloader,
      HttpHeaderProvider headerProvider,
      MarketplaceProperties marketplaceProperties,
      AuthAccountIdResolver authAccountIdResolver
  ) {
    super(
        MarketplaceType.OZON,
        downloader,
        headerProvider,
        resolver,
        marketplaceProperties,
        authAccountIdResolver
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

    String partitionKey = pricesPartitionKey(effectiveCursor, limit);

    Snapshot<OzonProductInfoPricesItemRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.DICT_OZON_PRODUCT_INFO_PRICES,
        body,
        partitionKey,
        OzonProductInfoPricesItemRaw.class
    );

    String nextCursor = SnapshotCursorExtractor.extractCursor(downloaded.file());
    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextCursor);
  }

  private static String pricesPartitionKey(String cursor, int limit) {
    String cursorTag = cursor.isBlank() ? "start" : shortHash(cursor);
    return "cursor_%s_limit_%d".formatted(cursorTag, limit);
  }

  private static String shortHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 6; i++) {
        hex.append(String.format("%02x", hash[i]));
      }
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to hash cursor", ex);
    }
  }
}
