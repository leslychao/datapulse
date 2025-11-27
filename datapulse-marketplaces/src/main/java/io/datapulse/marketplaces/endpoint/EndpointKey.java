package io.datapulse.marketplaces.endpoint;

public enum EndpointKey {
  DICT_WB_WAREHOUSES,
  DICT_OZON_WAREHOUSES;

  public String tag() {
    return name().toLowerCase();
  }
}
