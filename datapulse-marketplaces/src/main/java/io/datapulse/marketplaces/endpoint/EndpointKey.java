package io.datapulse.marketplaces.endpoint;

public enum EndpointKey {

  // ===== Wildberries =====
  DICT_WB_WAREHOUSES_FBW,
  DICT_WB_OFFICES_FBS,
  DICT_WB_WAREHOUSES_SELLER,

  DICT_WB_CATEGORIES_PARENT,
  DICT_WB_SUBJECTS,

  DICT_WB_TARIFFS_COMMISSION,
  DICT_WB_PRODUCTS,
  DICT_OZON_PRODUCT_INFO_LIST,

  /**
   * Отчёт о продажах по реализации (детализация)
   * GET /api/v5/supplier/reportDetailByPeriod
   */
  FACT_WB_SALES_REPORT_DETAIL_BY_PERIOD,
  FACT_WB_SUPPLIER_SALES,

  // ===== Ozon =====
  DICT_OZON_WAREHOUSES_FBS,
  DICT_OZON_CLUSTERS,
  DICT_OZON_CATEGORY_TREE,

  DICT_OZON_PRODUCT_INFO_PRICES,
  DICT_OZON_PRODUCTS,

  /**
   * POST /v3/posting/fbs/list
   */
  FACT_OZON_POSTING_FBS_LIST,

  /**
   * POST /v3/finance/transaction/list
   */
  FACT_OZON_FINANCE_TRANSACTION_LIST;

  public String tag() {
    return name().toLowerCase();
  }
}
