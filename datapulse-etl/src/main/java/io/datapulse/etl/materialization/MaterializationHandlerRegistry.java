package io.datapulse.etl.materialization;

import io.datapulse.etl.MarketplaceEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class MaterializationHandlerRegistry {

  private final Map<MarketplaceEvent, MaterializationHandler> handlerByEvent;

  public MaterializationHandlerRegistry(List<MaterializationHandler> registeredHandlers) {
    this.handlerByEvent = Map.copyOf(indexByEvent(registeredHandlers));
  }

  public Optional<MaterializationHandler> findFor(MarketplaceEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    return Optional.ofNullable(handlerByEvent.get(event));
  }

  private static EnumMap<MarketplaceEvent, MaterializationHandler> indexByEvent(
      List<MaterializationHandler> registeredHandlers
  ) {
    Objects.requireNonNull(registeredHandlers, "registeredHandlers must not be null");

    EnumMap<MarketplaceEvent, MaterializationHandler> index = new EnumMap<>(MarketplaceEvent.class);

    for (MaterializationHandler handler : registeredHandlers) {
      Objects.requireNonNull(handler, "handler must not be null");

      MarketplaceEvent event = Objects.requireNonNull(
          handler.supportedEvent(),
          "supportedEvent() must not return null: " + handler.getClass().getSimpleName()
      );

      MaterializationHandler alreadyRegistered = index.putIfAbsent(event, handler);
      if (alreadyRegistered != null) {
        throw new IllegalStateException(
            "Multiple materialization handlers registered for event %s: %s and %s"
                .formatted(
                    event,
                    alreadyRegistered.getClass().getSimpleName(),
                    handler.getClass().getSimpleName()
                )
        );
      }
    }

    return index;
  }
}
