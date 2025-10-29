package io.datapulse.etl.route;


import io.datapulse.marketplaces.event.EventFetcher;
import io.datapulse.marketplaces.event.transform.EventItemTransformer;

public final class Sources {

  private Sources() {
  }

  public static <R, D> EventSource<D> map(
      EventFetcher<R> fetcher,
      EventItemTransformer<R, D> transformer
  ) {
    return request -> fetcher.fetch(request).map(transformer::transform);
  }
}
