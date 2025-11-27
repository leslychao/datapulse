package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties.RetryPolicy;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

@Slf4j
public class WbRetryPolicy extends BaseRetryPolicy {

  @Override
  protected Duration computeHeaderDelay(HttpHeaders headers) {
    Duration xr = parseSeconds(headers.getFirst(HDR_X_RETRY));
    if (xr != null && !xr.isNegative()) {
      return xr;
    }

    Duration ra = parseRetryAfter(headers);
    if (ra != null && !ra.isNegative()) {
      return ra;
    }

    return null;
  }

  @Override
  protected Duration maxInMemory429Backoff(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      RetryPolicy cfg
  ) {
    if (marketplace == MarketplaceType.WILDBERRIES) {
      return Duration.ofSeconds(5);
    }
    return super.maxInMemory429Backoff(marketplace, endpoint, cfg);
  }
}
