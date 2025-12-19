package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.OzonCategoryTreeRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductInfoItemRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductListItemRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonFinanceTransactionOperationRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonPostingFbsRaw;
import io.datapulse.marketplaces.dto.raw.tariff.OzonProductInfoPricesItemRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.json.OzonCursorExtractor;
import io.datapulse.marketplaces.json.OzonHasNextExtractor;
import io.datapulse.marketplaces.json.OzonLastIdExtractor;
import io.datapulse.marketplaces.json.OzonPageCountExtractor;
import io.datapulse.marketplaces.service.AuthAccountIdResolver;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class OzonAdapter extends AbstractMarketplaceAdapter {

  private static final int MAX_INFO_LIST_BATCH_SIZE = 1000;

  private static final DateTimeFormatter RFC3339_MILLIS_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter RFC3339_SECONDS_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

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

  public Snapshot<OzonProductInfoItemRaw> downloadProductInfoListBatch(
      long accountId,
      List<Long> productIds
  ) {
    if (productIds.isEmpty()) {
      throw new IllegalArgumentException(
          "productIds list is empty: /v3/product/info/list will not be called.");
    }

    if (productIds.size() > MAX_INFO_LIST_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "Batch size for /v3/product/info/list is too large: " + productIds.size()
              + ". Maximum allowed: " + MAX_INFO_LIST_BATCH_SIZE + ".");
    }

    Map<String, Object> body = Map.of(
        "product_id", productIds,
        "offer_id", List.of(),
        "sku", List.of()
    );

    String partitionKey = partitionKeyForProductIds(productIds);

    return doPostPartitioned(
        accountId,
        EndpointKey.DICT_OZON_PRODUCT_INFO_LIST,
        body,
        partitionKey,
        OzonProductInfoItemRaw.class
    );
  }

  private String partitionKeyForProductIds(List<Long> productIds) {
    Long first = productIds.get(0);
    Long last = productIds.get(productIds.size() - 1);
    String cursorTag = "product_ids_" + first + "_" + last;
    return partitionKeyGenerator.generate(cursorTag, productIds.size());
  }

  public Snapshot<OzonPostingFbsRaw> downloadPostingsFbsPage(
      long accountId,
      LocalDate dateFrom,
      LocalDate dateTo,
      long offset,
      int limit
  ) {
    Map<String, Object> body = Map.of(
        "dir", "ASC",
        "filter", Map.of(
            "since", rfc3339StartOfDayUtcSeconds(dateFrom),
            "to", rfc3339EndOfDayUtcSeconds(dateTo)
        ),
        "limit", limit,
        "offset", offset,
        "with", Map.of(
            "financial_data", true,
            "analytics_data", false,
            "barcodes", false,
            "translit", false
        )
    );

    String offsetTag = String.valueOf(offset);
    String partitionKey = partitionKeyGenerator.generate(offsetTag, limit);

    Snapshot<OzonPostingFbsRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.FACT_OZON_POSTING_FBS_LIST,
        body,
        partitionKey,
        OzonPostingFbsRaw.class
    );

    boolean hasNext = OzonHasNextExtractor.extractHasNext(downloaded.file());
    String nextOffset = hasNext ? String.valueOf(offset + limit) : null;

    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextOffset);
  }

  public Snapshot<OzonFinanceTransactionOperationRaw> downloadFinanceTransactionsPage(
      long accountId,
      LocalDate dateFrom,
      LocalDate dateTo,
      long page,
      int pageSize
  ) {
    Map<String, Object> body = Map.of(
        "filter", Map.of(
            "date", Map.of(
                "from", rfc3339StartOfDayUtcMillis(dateFrom),
                "to", rfc3339StartOfDayUtcMillis(dateTo)
            ),
            "transaction_type", "all"
        ),
        "page", page,
        "page_size", pageSize
    );

    String pageTag = String.valueOf(page);
    String partitionKey = partitionKeyGenerator.generate(pageTag, pageSize);

    Snapshot<OzonFinanceTransactionOperationRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.FACT_OZON_FINANCE_TRANSACTION_LIST,
        body,
        partitionKey,
        OzonFinanceTransactionOperationRaw.class
    );

    long pageCount = OzonPageCountExtractor.extractPageCount(downloaded.file());
    String nextPage = (pageCount > 0 && page < pageCount) ? String.valueOf(page + 1) : null;

    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextPage);
  }

  private static String rfc3339StartOfDayUtcMillis(LocalDate date) {
    Instant instant = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    return RFC3339_MILLIS_UTC.format(instant);
  }

  private static String rfc3339StartOfDayUtcSeconds(LocalDate date) {
    Instant instant = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    return RFC3339_SECONDS_UTC.format(instant);
  }

  private static String rfc3339EndOfDayUtcSeconds(LocalDate date) {
    Instant instant = date.plusDays(1).atStartOfDay().minusSeconds(1).toInstant(ZoneOffset.UTC);
    return RFC3339_SECONDS_UTC.format(instant);
  }
}
