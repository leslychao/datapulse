package io.datapulse.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MarketplaceType {
  WILDBERRIES, OZON;

  @JsonCreator
  public static MarketplaceType from(String raw) {
    return MarketplaceParser.parseOrThrow(raw);
  }
}
