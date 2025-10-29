package io.datapulse.etl.route;

import io.datapulse.marketplaces.event.BusinessEvent;
import io.datapulse.marketplaces.event.FetchRequest;
import reactor.core.publisher.Flux;

public interface EventSource<D> {

  Flux<D> fetch(FetchRequest request);

  BusinessEvent event();
}
