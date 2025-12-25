package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.OzonCategoryTreeRaw;
import io.datapulse.marketplaces.dto.raw.inventory.OzonAnalyticsStocksRaw;
import io.datapulse.marketplaces.dto.raw.inventory.OzonProductInfoStocksRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductInfoItemRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductListItemRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonFinanceTransactionOperationRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonPostingFboRaw;
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
import io.datapulse.marketplaces.json.OzonResultSizeExtractor;
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

  private static final int MAX_ANALYTICS_STOCKS_SKUS_SIZE = 100;

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

  // ===== Warehouses =====

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

  // ===== Categories =====

  public Snapshot<OzonCategoryTreeRaw> downloadCategoryTree(long accountId) {
    return doPost(
        accountId,
        EndpointKey.DICT_OZON_CATEGORY_TREE,
        Map.of("language", "DEFAULT"),
        OzonCategoryTreeRaw.class
    );
  }

  // ===== Products / Prices =====

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

  // ===== Sales =====

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

  public Snapshot<OzonPostingFboRaw> downloadPostingsFboPage(
      long accountId,
      LocalDate dateFrom,
      LocalDate dateTo,
      long offset,
      int limit,
      String status
  ) {
    String effectiveStatus = status == null ? "" : status;

    Map<String, Object> body = Map.of(
        "dir", "ASC",
        "filter", Map.of(
            "since", rfc3339StartOfDayUtcMillis(dateFrom),
            "to", rfc3339EndOfDayUtcMillis(dateTo),
            "status", effectiveStatus
        ),
        "limit", limit,
        "offset", offset,
        "translit", false,
        "with", Map.of(
            "analytics_data", true,
            "financial_data", true,
            "legal_info", false
        )
    );

    String offsetTag = String.valueOf(offset);
    String partitionKey = partitionKeyGenerator.generate(offsetTag, limit);

    Snapshot<OzonPostingFboRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.FACT_OZON_POSTING_FBO_LIST,
        body,
        partitionKey,
        OzonPostingFboRaw.class
    );

    int resultSize = OzonResultSizeExtractor.extractResultSize(downloaded.file());
    boolean hasNext = resultSize == limit;

    long nextOffsetValue = offset + limit;
    boolean canPaginateByOffset = nextOffsetValue <= 20000L;

    String nextOffset = (hasNext && canPaginateByOffset) ? String.valueOf(nextOffsetValue) : null;

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

  // ===== Supply Chain: Inventory (Ozon) =====

  public Snapshot<OzonProductInfoStocksRaw> downloadProductInfoStocksPage(
      long accountId,
      String cursor,
      int limit,
      List<String> offerIds,
      List<Long> productIds
  ) {
    String effectiveCursor = cursor == null ? "" : cursor;

    Map<String, Object> body = Map.of(
        "cursor", effectiveCursor,
        "filter", Map.of(
            "offer_id", offerIds,
            "product_id", productIds,
            "visibility", "ALL",
            "with_quant", Map.of(
                "created", true,
                "exists", true
            )
        ),
        "limit", limit
    );

    String partitionKey = partitionKeyGenerator.generate(effectiveCursor, limit);

    Snapshot<OzonProductInfoStocksRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.FACT_OZON_PRODUCT_INFO_STOCKS,
        body,
        partitionKey,
        OzonProductInfoStocksRaw.class
    );

    String nextCursor = OzonCursorExtractor.extractCursor(downloaded.file());
    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextCursor);
  }

  public Snapshot<OzonAnalyticsStocksRaw> downloadAnalyticsStocks(
      long accountId,
      List<Long> skus
  ) {
    if (skus == null || skus.isEmpty()) {
      throw new IllegalArgumentException("Ozon /v1/analytics/stocks requires skus (1..100).");
    }
    if (skus.size() > MAX_ANALYTICS_STOCKS_SKUS_SIZE) {
      throw new IllegalArgumentException(
          "Ozon /v1/analytics/stocks skus limit exceeded: " + skus.size()
              + " (max " + MAX_ANALYTICS_STOCKS_SKUS_SIZE + ").");
    }

    Map<String, Object> body = Map.of(
        "skus", skus
    );

    return doPost(
        accountId,
        EndpointKey.FACT_OZON_ANALYTICS_STOCKS,
        body,
        OzonAnalyticsStocksRaw.class
    );
  }

  // ===== RFC3339 helpers =====

  private static String rfc3339EndOfDayUtcMillis(LocalDate date) {
    Instant instant = date.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);
    return RFC3339_MILLIS_UTC.format(instant);
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
