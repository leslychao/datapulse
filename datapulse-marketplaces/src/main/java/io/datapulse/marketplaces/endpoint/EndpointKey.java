package io.datapulse.marketplaces.endpoint;

public enum EndpointKey {
  DICT_WB_WAREHOUSES,
  DICT_OZON_WAREHOUSES,
  WB_TARIFF_COMMISSION,
  WB_TARIFF_BOX,
  WB_TARIFF_MONOPALLET;

  public String tag() {
    return name().toLowerCase();
  }
}
