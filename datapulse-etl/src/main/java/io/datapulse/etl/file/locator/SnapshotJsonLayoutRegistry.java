package io.datapulse.etl.file.locator;

import io.datapulse.marketplaces.dto.raw.category.OzonCategoryTreeRaw;
import io.datapulse.marketplaces.dto.raw.category.WbCategoryParentListRaw;
import io.datapulse.marketplaces.dto.raw.category.WbSubjectListRaw;
import io.datapulse.marketplaces.dto.raw.inventory.OzonAnalyticsStocksRaw;
import io.datapulse.marketplaces.dto.raw.inventory.OzonProductInfoStocksRaw;
import io.datapulse.marketplaces.dto.raw.inventory.WbStockRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductInfoItemRaw;
import io.datapulse.marketplaces.dto.raw.product.OzonProductListItemRaw;
import io.datapulse.marketplaces.dto.raw.product.WbProductCardRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonFinanceTransactionOperationRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonPostingFboRaw;
import io.datapulse.marketplaces.dto.raw.sales.OzonPostingFbsRaw;
import io.datapulse.marketplaces.dto.raw.sales.WbSalesReportDetailRowRaw;
import io.datapulse.marketplaces.dto.raw.sales.WbSupplierSaleRaw;
import io.datapulse.marketplaces.dto.raw.supply.WbIncomeRaw;
import io.datapulse.marketplaces.dto.raw.tariff.OzonProductInfoPricesItemRaw;
import io.datapulse.marketplaces.dto.raw.tariff.WbTariffCommissionRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.warehouse.wb.WbWarehouseSellerListRaw;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
public final class SnapshotJsonLayoutRegistry {

  private final Map<Class<?>, JsonArrayLocator> locators = new ConcurrentHashMap<>();

  public SnapshotJsonLayoutRegistry() {

    // ===== Warehouses =====
    register(OzonWarehouseFbsListRaw.class, JsonArrayLocators.arrayAtPath("result"));
    register(OzonClusterListRaw.class, JsonArrayLocators.arrayAtPath("clusters"));
    register(WbWarehouseFbwListRaw.class, JsonArrayLocators.arrayAtPath());
    register(WbOfficeFbsListRaw.class, JsonArrayLocators.arrayAtPath());
    register(WbWarehouseSellerListRaw.class, JsonArrayLocators.arrayAtPath());

    // ===== Categories =====
    register(OzonCategoryTreeRaw.class, JsonArrayLocators.arrayAtPath("result"));
    register(WbCategoryParentListRaw.class, JsonArrayLocators.arrayAtPath("data"));
    register(WbSubjectListRaw.class, JsonArrayLocators.arrayAtPath("data"));

    // ===== Tariffs / Commissions =====
    register(WbTariffCommissionRaw.class, JsonArrayLocators.arrayAtPath("report"));
    register(OzonProductInfoPricesItemRaw.class, JsonArrayLocators.arrayAtPath("items"));

    // ===== Products =====
    register(OzonProductListItemRaw.class, JsonArrayLocators.arrayAtPath("result", "items"));
    register(WbProductCardRaw.class, JsonArrayLocators.arrayAtPath("cards"));
    register(OzonProductInfoItemRaw.class, JsonArrayLocators.arrayAtPath("items"));

    // ===== Sales =====
    register(WbSalesReportDetailRowRaw.class, JsonArrayLocators.arrayAtPath());
    register(OzonPostingFbsRaw.class, JsonArrayLocators.arrayAtPath("result.postings"));
    register(OzonPostingFboRaw.class, JsonArrayLocators.arrayAtPath("result"));
    register(OzonFinanceTransactionOperationRaw.class,
        JsonArrayLocators.arrayAtPath("result.operations"));
    register(WbSupplierSaleRaw.class, JsonArrayLocators.arrayAtPath());

    // ===== Supply Chain =====
    register(WbStockRaw.class, JsonArrayLocators.arrayAtPath());
    register(WbIncomeRaw.class, JsonArrayLocators.arrayAtPath());
    register(OzonProductInfoStocksRaw.class, JsonArrayLocators.arrayAtPath("items"));
    register(OzonAnalyticsStocksRaw.class, JsonArrayLocators.arrayAtPath("items"));

  }

  public void register(@NonNull Class<?> rawType, @NonNull JsonArrayLocator locator) {
    locators.put(rawType, locator);
  }

  public JsonArrayLocator resolve(@NonNull Class<?> rawType) {
    return locators.getOrDefault(rawType, JsonArrayLocators.arrayAtPath());
  }
}
