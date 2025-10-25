package io.datapulse.domain.dto.credentials;

import io.datapulse.domain.MarketplaceType;

public record WbCredentials(String token) implements MarketplaceCredentials {

  @Override
  public String type() {
    return "WB";
  }
}
