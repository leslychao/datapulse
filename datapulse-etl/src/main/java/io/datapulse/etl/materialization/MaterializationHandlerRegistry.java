package io.datapulse.etl.materialization;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class MaterializationHandlerRegistry {

  private final Map<MarketplaceEvent, Map<MarketplaceType, MaterializationHandler>> handlerByEventAndMarketplace;
  private final Map<MarketplaceEvent, List<MaterializationHandler>> handlersByEvent;

  public MaterializationHandlerRegistry(List<MaterializationHandler> registeredHandlers) {
    Objects.requireNonNull(registeredHandlers, "registeredHandlers must not be null");

    EnumMap<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> index =
        buildIndex(registeredHandlers);

    this.handlerByEventAndMarketplace = freezeIndex(index);
    this.handlersByEvent = freezeLists(index);
  }

  public List<MaterializationHandler> findFor(MarketplaceEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    return handlersByEvent.getOrDefault(event, List.of());
  }

  public MaterializationHandler findFor(MarketplaceEvent event, MarketplaceType marketplace) {
    Objects.requireNonNull(event, "event must not be null");
    Objects.requireNonNull(marketplace, "marketplace must not be null");

    Map<MarketplaceType, MaterializationHandler> byMarketplace =
        handlerByEventAndMarketplace.get(event);
    if (byMarketplace == null) {
      return null;
    }
    return byMarketplace.get(marketplace);
  }

  private static EnumMap<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> buildIndex(
      List<MaterializationHandler> registeredHandlers
  ) {
    EnumMap<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> index =
        new EnumMap<>(MarketplaceEvent.class);

    for (MaterializationHandler handler : registeredHandlers) {
      Objects.requireNonNull(handler, "handler must not be null");

      MarketplaceEvent event = requireSupportedEvent(handler);
      MarketplaceType marketplace = requireMarketplace(handler);

      EnumMap<MarketplaceType, MaterializationHandler> byMarketplace =
          index.computeIfAbsent(event, ignored -> new EnumMap<>(MarketplaceType.class));

      MaterializationHandler previous = byMarketplace.putIfAbsent(marketplace, handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Multiple materialization handlers registered for event=%s marketplace=%s: %s and %s"
                .formatted(
                    event,
                    marketplace,
                    previous.getClass().getSimpleName(),
                    handler.getClass().getSimpleName()
                )
        );
      }
    }

    return index;
  }

  private static MarketplaceEvent requireSupportedEvent(MaterializationHandler handler) {
    MarketplaceEvent event = handler.supportedEvent();
    if (event == null) {
      throw new IllegalStateException(
          "MaterializationHandler returned null supportedEvent: " + handler.getClass()
              .getSimpleName()
      );
    }
    return event;
  }

  private static MarketplaceType requireMarketplace(MaterializationHandler handler) {
    MarketplaceType marketplace = handler.marketplace();
    if (marketplace == null) {
      throw new IllegalStateException(
          "MaterializationHandler returned null marketplace: " + handler.getClass().getSimpleName()
      );
    }
    return marketplace;
  }

  private static Map<MarketplaceEvent, Map<MarketplaceType, MaterializationHandler>> freezeIndex(
      EnumMap<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> index
  ) {
    EnumMap<MarketplaceEvent, Map<MarketplaceType, MaterializationHandler>> frozen =
        new EnumMap<>(MarketplaceEvent.class);

    for (Map.Entry<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> entry : index.entrySet()) {
      frozen.put(entry.getKey(), Map.copyOf(entry.getValue()));
    }
    return Map.copyOf(frozen);
  }

  private static Map<MarketplaceEvent, List<MaterializationHandler>> freezeLists(
      EnumMap<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> index
  ) {
    EnumMap<MarketplaceEvent, List<MaterializationHandler>> lists = new EnumMap<>(
        MarketplaceEvent.class);

    for (Map.Entry<MarketplaceEvent, EnumMap<MarketplaceType, MaterializationHandler>> entry : index.entrySet()) {
      lists.put(entry.getKey(), toDeterministicList(entry.getValue()));
    }

    return Map.copyOf(lists);
  }

  private static List<MaterializationHandler> toDeterministicList(
      EnumMap<MarketplaceType, MaterializationHandler> byMarketplace
  ) {
    if (byMarketplace.isEmpty()) {
      return List.of();
    }

    List<MaterializationHandler> ordered = new ArrayList<>(byMarketplace.size());
    for (MarketplaceType marketplace : MarketplaceType.values()) {
      MaterializationHandler handler = byMarketplace.get(marketplace);
      if (handler != null) {
        ordered.add(handler);
      }
    }
    return List.copyOf(ordered);
  }
}
