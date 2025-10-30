package io.datapulse.etl.route;

import io.datapulse.marketplaces.event.MarketplaceEvent;
import io.datapulse.marketplaces.event.FetchRequest;
import reactor.core.publisher.Flux;

public interface EventSource<D> {

  Flux<D> fetch(FetchRequest request);

  MarketplaceEvent event();
}
