package io.datapulse.etl.route;

import io.datapulse.marketplaces.event.BusinessEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class EventRoutesRegistry {

  private final Map<BusinessEvent, EventRoute<?>> routes = new EnumMap<>(BusinessEvent.class);

  public <D> void register(BusinessEvent event, EventRoute<D> route) {
    routes.put(event, route);
  }

  public Optional<EventRoute<?>> route(BusinessEvent event) {
    return Optional.ofNullable(routes.get(event));
  }
}
