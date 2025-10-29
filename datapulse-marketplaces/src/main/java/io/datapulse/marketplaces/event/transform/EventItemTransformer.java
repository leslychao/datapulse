package io.datapulse.marketplaces.event.transform;

public interface EventItemTransformer<R, D> {

  Class<R> rawType();

  D transform(R raw);
}
