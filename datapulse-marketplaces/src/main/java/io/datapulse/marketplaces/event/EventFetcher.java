package io.datapulse.marketplaces.event;

import io.datapulse.domain.MarketplaceType;
import reactor.core.publisher.Flux;

public interface EventFetcher<R> {

  BusinessEvent event();

  MarketplaceType marketplace();

  Flux<R> fetch(FetchRequest request);
}
