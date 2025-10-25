package io.datapulse.domain.dto.credentials;

import io.datapulse.domain.MarketplaceType;

public record OzonCredentials(String apiKey, String clientId)
    implements MarketplaceCredentials {

  @Override
  public String type() {
    return "OZON";
  }
}
