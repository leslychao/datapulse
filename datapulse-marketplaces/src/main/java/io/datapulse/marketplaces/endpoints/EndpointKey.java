package io.datapulse.marketplaces.endpoints;

public enum EndpointKey {
  SALES, STOCK, FINANCE, REVIEWS;

  public String tag() {
    return name().toLowerCase();
  }
}
