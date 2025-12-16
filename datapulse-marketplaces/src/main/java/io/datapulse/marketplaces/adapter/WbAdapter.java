package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.WbCategoryParentListRaw;
import io.datapulse.marketplaces.dto.raw.category.WbSubjectListRaw;
import io.datapulse.marketplaces.dto.raw.product.WbProductCardRaw;
import io.datapulse.marketplaces.dto.raw.tariff.WbTariffCommissionRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseSellerListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.json.WbCardsCursorExtractor;
import io.datapulse.marketplaces.service.AuthAccountIdResolver;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class WbAdapter extends AbstractMarketplaceAdapter {

  private final CursorLimitPartitionKeyGenerator partitionKeyGenerator;

  public WbAdapter(
      EndpointsResolver resolver,
      MarketplaceStreamingDownloadService downloader,
      HttpHeaderProvider headerProvider,
      MarketplaceProperties marketplaceProperties,
      AuthAccountIdResolver authAccountIdResolver,
      CursorLimitPartitionKeyGenerator partitionKeyGenerator
  ) {
    super(
        MarketplaceType.WILDBERRIES,
        downloader,
        headerProvider,
        resolver,
        marketplaceProperties,
        authAccountIdResolver
    );
    this.partitionKeyGenerator = partitionKeyGenerator;
  }

  public Snapshot<WbWarehouseFbwListRaw> downloadFbwWarehouses(long accountId) {
    return doGet(
        accountId,
        EndpointKey.DICT_WB_WAREHOUSES_FBW,
        WbWarehouseFbwListRaw.class
    );
  }

  public Snapshot<WbOfficeFbsListRaw> downloadFbsOffices(long accountId) {
    return doGet(
        accountId,
        EndpointKey.DICT_WB_OFFICES_FBS,
        WbOfficeFbsListRaw.class
    );
  }

  public Snapshot<WbWarehouseSellerListRaw> downloadSellerWarehouses(long accountId) {
    return doGet(
        accountId,
        EndpointKey.DICT_WB_WAREHOUSES_SELLER,
        WbWarehouseSellerListRaw.class
    );
  }

  public Snapshot<WbCategoryParentListRaw> downloadParentCategories(long accountId) {
    return doGet(accountId, EndpointKey.DICT_WB_CATEGORIES_PARENT, WbCategoryParentListRaw.class);
  }

  public Snapshot<WbSubjectListRaw> downloadSubjects(long accountId) {
    return doGet(accountId, EndpointKey.DICT_WB_SUBJECTS, WbSubjectListRaw.class);
  }

  public Snapshot<WbTariffCommissionRaw> downloadTariffsCommission(long accountId) {
    return doGet(
        accountId,
        EndpointKey.DICT_WB_TARIFFS_COMMISSION,
        WbTariffCommissionRaw.class
    );
  }

  public Snapshot<WbProductCardRaw> downloadProductCards(
      long accountId,
      String cursor,
      int limit
  ) {
    String effectiveCursor = normalizeCursor(cursor);

    Map<String, Object> cursorBody = effectiveCursor == null
        ? Map.of("limit", limit)
        : Map.of(
            "updatedAt", cursorUpdatedAt(effectiveCursor),
            "nmID", cursorNmId(effectiveCursor),
            "limit", limit
        );

    Map<String, Object> body = Map.of(
        "settings", Map.of(
            "sort", Map.of("ascending", false),
            "filter", Map.of("withPhoto", -1),
            "cursor", cursorBody
        )
    );

    String partitionKey = partitionKeyGenerator.generate(effectiveCursor == null ? "" : effectiveCursor, limit);

    Snapshot<WbProductCardRaw> downloaded = doPostPartitioned(
        accountId,
        EndpointKey.DICT_WB_PRODUCTS,
        body,
        partitionKey,
        WbProductCardRaw.class
    );

    String nextCursor = WbCardsCursorExtractor.extractCursor(downloaded.file());
    return new Snapshot<>(downloaded.elementType(), downloaded.file(), nextCursor);
  }

  private static String normalizeCursor(String cursor) {
    if (cursor == null) {
      return null;
    }
    String trimmed = cursor.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String cursorUpdatedAt(String cursor) {
    int sep = cursor.indexOf('|');
    if (sep <= 0) {
      throw new IllegalArgumentException("Invalid WB cursor format: expected 'updatedAt|nmID'");
    }
    return cursor.substring(0, sep);
  }

  private static long cursorNmId(String cursor) {
    int sep = cursor.indexOf('|');
    if (sep <= 0 || sep == cursor.length() - 1) {
      throw new IllegalArgumentException("Invalid WB cursor format: expected 'updatedAt|nmID'");
    }
    return Long.parseLong(cursor.substring(sep + 1));
  }
}
