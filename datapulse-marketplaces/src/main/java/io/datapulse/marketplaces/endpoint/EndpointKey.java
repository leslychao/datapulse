package io.datapulse.marketplaces.endpoint;

public enum EndpointKey {

  DICT_WB_WAREHOUSES_FBW,
  DICT_WB_OFFICES_FBS,
  DICT_WB_WAREHOUSES_SELLER,

  DICT_OZON_WAREHOUSES_FBS,
  DICT_OZON_CLUSTERS,

  DICT_OZON_CATEGORY_TREE,

  DICT_WB_CATEGORIES_PARENT,
  DICT_WB_SUBJECTS,

  DICT_WB_TARIFFS_COMMISSION;

  public String tag() {
    return name().toLowerCase();
  }
}
