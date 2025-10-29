package io.datapulse.etl.route;

import io.datapulse.marketplaces.event.FetchRequest;
import java.util.List;
import reactor.core.publisher.Flux;

public final class EventRoute<D> {

  private final List<EventSource<D>> sources;

  public EventRoute(List<EventSource<D>> sources) {
    this.sources = List.copyOf(sources);
  }

  public Flux<D> fetchAll(FetchRequest request) {
    return Flux.merge(sources.stream().map(s -> s.fetch(request)).toList());
  }
}
