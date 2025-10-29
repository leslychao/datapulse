package io.datapulse.marketplaces.endpoint;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.event.BusinessEvent;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Перекрываемая мапа соответствий эвент → ключ эндпоинта по каждому маркетплейсу.
 */
@Component
public class EventEndpointRegistry {

  private final Map<MarketplaceType, Map<BusinessEvent, EndpointKey>> mapping = new EnumMap<>(
      MarketplaceType.class);

  public EventEndpointRegistry() {
    // дефолтные соответствия (минимальные; расширяем по мере интеграции)
    Map<BusinessEvent, EndpointKey> common = new EnumMap<>(BusinessEvent.class);
    common.put(BusinessEvent.SALES_FACT, EndpointKey.SALES);
    common.put(BusinessEvent.STOCK_LEVEL, EndpointKey.STOCK);
    common.put(BusinessEvent.REVIEW, EndpointKey.REVIEWS);
    common.put(BusinessEvent.RETURN,
        EndpointKey.FINANCE);       // до появления отдельного возвратного API
    common.put(BusinessEvent.AD_PERFORMANCE, EndpointKey.FINANCE); // временно
    // специфические карты можно скопировать из common
    mapping.put(MarketplaceType.WILDBERRIES, new EnumMap<>(common));
    mapping.put(MarketplaceType.OZON, new EnumMap<>(common));
  }

  public EndpointKey endpointKey(MarketplaceType type, BusinessEvent event) {
    var byMp = mapping.get(type);
    if (byMp == null || !byMp.containsKey(event)) {
      throw new IllegalArgumentException("No endpoint mapping for " + type + " and event " + event);
    }
    return byMp.get(event);
  }
}
