package io.datapulse.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;

public enum MarketplaceType {

  WILDBERRIES(WbCredentials.class),
  OZON(OzonCredentials.class);

  private final Class<? extends MarketplaceCredentials> credentialsType;

  MarketplaceType(Class<? extends MarketplaceCredentials> credentialsType) {
    this.credentialsType = credentialsType;
  }

  public Class<? extends MarketplaceCredentials> credentialsType() {
    return credentialsType;
  }

  public boolean supports(MarketplaceCredentials credentials) {
    return credentialsType.isInstance(credentials);
  }

  public String tag() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static MarketplaceType from(String raw) {
    return MarketplaceParser.parseOrThrow(raw);
  }
}
