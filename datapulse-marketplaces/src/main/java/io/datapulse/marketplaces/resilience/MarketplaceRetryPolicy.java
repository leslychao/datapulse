package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties.RetryPolicy;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import reactor.util.retry.Retry;

public interface MarketplaceRetryPolicy {

  Retry retryFor(MarketplaceType marketplace, EndpointKey endpoint, RetryPolicy cfg);
}
