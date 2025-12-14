package io.datapulse.marketplaces.endpoint;

public enum EndpointKey {

  // ===== Wildberries =====

  DICT_WB_WAREHOUSES_FBW(EndpointAuthScope.SANDBOX_ACCOUNT),
  DICT_WB_OFFICES_FBS(EndpointAuthScope.SANDBOX_ACCOUNT),
  DICT_WB_WAREHOUSES_SELLER(EndpointAuthScope.SANDBOX_ACCOUNT),

  DICT_WB_CATEGORIES_PARENT(EndpointAuthScope.SANDBOX_ACCOUNT),
  DICT_WB_SUBJECTS(EndpointAuthScope.SANDBOX_ACCOUNT),

  DICT_WB_TARIFFS_COMMISSION(EndpointAuthScope.SYSTEM_ACCOUNT),

  // ===== Ozon =====

  DICT_OZON_WAREHOUSES_FBS(EndpointAuthScope.TARGET_ACCOUNT),
  DICT_OZON_CLUSTERS(EndpointAuthScope.SYSTEM_ACCOUNT),
  DICT_OZON_CATEGORY_TREE(EndpointAuthScope.SYSTEM_ACCOUNT);

  private final EndpointAuthScope authScope;

  EndpointKey(EndpointAuthScope authScope) {
    this.authScope = authScope;
  }

  public EndpointAuthScope authScope() {
    return authScope;
  }

  public String tag() {
    return name().toLowerCase();
  }
}
