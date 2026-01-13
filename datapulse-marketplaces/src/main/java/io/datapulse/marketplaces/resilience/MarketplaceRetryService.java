package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.config.MarketplaceProperties.RetryPolicy;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class MarketplaceRetryService {

  private final MarketplaceProperties props;

  private final MarketplaceRetryPolicy basePolicy = new BaseRetryPolicy() {
  };

  private final Map<MarketplaceType, MarketplaceRetryPolicy> policyByMp = initPolicies();

  private Map<MarketplaceType, MarketplaceRetryPolicy> initPolicies() {
    Map<MarketplaceType, MarketplaceRetryPolicy> map =
        new EnumMap<>(MarketplaceType.class);
    map.put(MarketplaceType.WILDBERRIES, new WbRetryPolicy());
    return map;
  }

  public <T> Flux<T> withRetries(
      Flux<T> source,
      MarketplaceType marketplace,
      EndpointKey endpoint
  ) {
    return source.retryWhen(retryFor(marketplace, endpoint));
  }

  private Retry retryFor(MarketplaceType marketplace, EndpointKey endpoint) {
    RetryPolicy cfg = props.get(marketplace).effectiveRetryPolicy(endpoint);
    return getPolicy(marketplace).retryFor(marketplace, endpoint, cfg);
  }

  private MarketplaceRetryPolicy getPolicy(MarketplaceType marketplace) {
    return policyByMp.getOrDefault(marketplace, basePolicy);
  }
}
