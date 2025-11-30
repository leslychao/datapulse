package io.datapulse.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MarketplaceType {
  WILDBERRIES, OZON;

  public String tag() {
    return name().toLowerCase();
  }
  
  @JsonCreator
  public static MarketplaceType from(String raw) {
    return MarketplaceParser.parseOrThrow(raw);
  }
}
