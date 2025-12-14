package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.category.WbCategoryParentListRaw;
import io.datapulse.marketplaces.dto.raw.category.WbSubjectListRaw;
import io.datapulse.marketplaces.dto.raw.tariff.WbTariffCommissionRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseSellerListRaw;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.service.AuthAccountIdResolver;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import org.springframework.stereotype.Component;

@Component
public final class WbAdapter extends AbstractMarketplaceAdapter {

  public WbAdapter(
      EndpointsResolver resolver,
      MarketplaceStreamingDownloadService downloader,
      HttpHeaderProvider headerProvider,
      MarketplaceProperties marketplaceProperties,
      AuthAccountIdResolver authAccountIdResolver
  ) {
    super(
        MarketplaceType.WILDBERRIES,
        downloader,
        headerProvider,
        resolver,
        marketplaceProperties,
        authAccountIdResolver
    );
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
}
