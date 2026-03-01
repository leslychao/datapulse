package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import lombok.Getter;

public final class LocalRateLimitBackoffRequiredException extends RuntimeException {

  @Getter
  private final MarketplaceType marketplace;

  @Getter
  private final EndpointKey endpoint;

  @Getter
  private final int retryAfterSeconds;

  public LocalRateLimitBackoffRequiredException(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      int retryAfterSeconds,
      String message
  ) {
    super(message);
    this.marketplace = marketplace;
    this.endpoint = endpoint;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}