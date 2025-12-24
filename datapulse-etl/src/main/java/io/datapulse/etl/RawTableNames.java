package io.datapulse.etl;

public final class RawTableNames {

  private RawTableNames() {
  }

  // ===== Ozon: Warehouses =====
  public static final String RAW_OZON_WAREHOUSES_FBS = "raw_ozon_warehouses_fbs";
  public static final String RAW_OZON_WAREHOUSES_FBO = "raw_ozon_warehouses_fbo";

  // ===== Wildberries: Warehouses =====
  public static final String RAW_WB_WAREHOUSES_FBW = "raw_wb_warehouses_fbw";
  public static final String RAW_WB_OFFICES_FBS = "raw_wb_offices_fbs";
  public static final String RAW_WB_WAREHOUSES_SELLER = "raw_wb_warehouses_seller";

  // ===== Categories =====
  public static final String RAW_OZON_CATEGORY_TREE = "raw_ozon_category_tree";
  public static final String RAW_WB_CATEGORIES_PARENT = "raw_wb_categories_parent";
  public static final String RAW_WB_SUBJECTS = "raw_wb_subjects";

  // ===== Commissions / Tariffs =====
  public static final String RAW_WB_TARIFFS_COMMISSION = "raw_wb_tariffs_commission";
  public static final String RAW_OZON_PRODUCT_INFO_PRICES = "raw_ozon_product_info_prices";

  // ===== Products =====
  public static final String RAW_OZON_PRODUCTS = "raw_ozon_products";
  public static final String RAW_WB_PRODUCTS = "raw_wb_products";
  public static final String RAW_OZON_PRODUCT_INFO = "raw_ozon_product_info";

  // ===== Sales fact (RAW) =====
  public static final String RAW_WB_SUPPLIER_SALES = "raw_wb_supplier_sales";
  public static final String RAW_WB_SALES_REPORT_DETAIL = "raw_wb_sales_report_detail";
  public static final String RAW_OZON_POSTINGS_FBS = "raw_ozon_postings_fbs";
  public static final String RAW_OZON_POSTINGS_FBO = "raw_ozon_postings_fbo";
  public static final String RAW_OZON_FINANCE_TRANSACTIONS = "raw_ozon_finance_transactions";

  // ===== Inventory / Supply Chain (RAW) =====
  public static final String RAW_WB_STOCKS = "raw_wb_stocks";
  public static final String RAW_WB_INCOMES = "raw_wb_incomes";
  public static final String RAW_OZON_PRODUCT_INFO_STOCKS = "raw_ozon_product_info_stocks";
  public static final String RAW_OZON_ANALYTICS_STOCKS = "raw_ozon_analytics_stocks";
}
