package io.datapulse.marketplaces.resilience;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.config.MarketplaceProperties.Resilience;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
public class MarketplaceRetryService {

  private final MarketplaceProperties props;

  private final Map<MarketplaceType, MarketplaceRetryPolicy> policyByMp = initPolicies();

  private Map<MarketplaceType, MarketplaceRetryPolicy> initPolicies() {
    var m = new EnumMap<MarketplaceType, MarketplaceRetryPolicy>(MarketplaceType.class);
    var common = new CommonRetryPolicy();
    m.put(MarketplaceType.OZON, common);
    m.put(MarketplaceType.WILDBERRIES, new WbRetryPolicy());
    // новые маркеты по умолчанию → common (см. getPolicy)
    return m;
  }

  public <T> Flux<T> withRetries(Flux<T> source, MarketplaceType marketplace, EndpointKey endpoint) {
    return source.retryWhen(retryFor(marketplace, endpoint));
  }

  public <T> Mono<T> withRetries(Mono<T> source, MarketplaceType marketplace, EndpointKey endpoint) {
    return source.retryWhen(retryFor(marketplace, endpoint));
  }

  public Retry retryFor(MarketplaceType marketplace, EndpointKey endpoint) {
    Resilience cfg = props.get(marketplace).effectiveResilience(endpoint);
    return getPolicy(marketplace).retryFor(marketplace, endpoint, cfg);
  }

  private MarketplaceRetryPolicy getPolicy(MarketplaceType marketplace) {
    return policyByMp.getOrDefault(marketplace, new CommonRetryPolicy());
  }
}
