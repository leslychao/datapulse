package io.datapulse.marketplaces.endpoint;

public enum EndpointKey {
  SALES, PRODUCT, STOCK, FINANCE, REVIEWS;

  public String tag() {
    return name().toLowerCase();
  }
}
