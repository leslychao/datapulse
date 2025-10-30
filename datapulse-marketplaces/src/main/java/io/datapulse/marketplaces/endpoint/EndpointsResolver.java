package io.datapulse.marketplaces.endpoint;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.event.BusinessEvent;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Минималистичный resolver: 1→N сопоставление события → ключи эндпоинтов, построение URI c учётом
 * sandbox и особого host для WB REVIEWS.
 */
@Component
public final class EndpointsResolver {

  private final MarketplaceProperties properties;

  /**
   * дефолтная карта 1→N (расширяй по мере необходимости)
   */
  private static final Map<BusinessEvent, List<EndpointKey>> DEFAULT_KEYS = Map.of(
      BusinessEvent.SALES_FACT, List.of(EndpointKey.SALES),
      BusinessEvent.STOCK_LEVEL, List.of(EndpointKey.STOCK),
      BusinessEvent.REVIEW, List.of(EndpointKey.REVIEWS),
      BusinessEvent.RETURN, List.of(EndpointKey.FINANCE),
      BusinessEvent.AD_PERFORMANCE, List.of(EndpointKey.FINANCE),
      BusinessEvent.ORDER_POSTING, List.of(EndpointKey.FINANCE),
      BusinessEvent.PRICE_SNAPSHOT, List.of(EndpointKey.FINANCE),
      BusinessEvent.CATALOG_ITEM, List.of(EndpointKey.FINANCE)
  );

  public EndpointsResolver(@NonNull MarketplaceProperties props) {
    this.properties = props;
  }

  /**
   * Все потенциальные endpoints для события (без query)
   */
  public List<EndpointRef> resolveAll(@NonNull MarketplaceType type, @NonNull BusinessEvent event) {
    var keys = DEFAULT_KEYS.get(event);
    if (keys == null || keys.isEmpty()) {
      throw new AppException("MARKETPLACE_EVENT_ENDPOINTS_MISSING", event.name());
    }
    var provider = properties.get(type);
    return keys.stream()
        .map(k -> new EndpointRef(k,
            buildUri(host(provider, type, k), required(provider.endpoint(k), k.name()))))
        .toList();
  }

  /**
   * То же, но применяем общий query ко всем
   */
  public List<EndpointRef> resolveAll(@NonNull MarketplaceType type,
      @NonNull BusinessEvent event,
      @NonNull Map<String, ?> query) {
    return resolveAll(type, event).stream()
        .map(ref -> new EndpointRef(ref.key(), applyQuery(ref.uri(), query)))
        .toList();
  }

  // --- helpers ---

  private static URI applyQuery(URI base, Map<String, ?> query) {
    var b = UriComponentsBuilder.fromUri(base);
    query.forEach((k, v) -> {
      if (v == null) {
        return;
      }
      if (v instanceof Iterable<?> it) {
        it.forEach(val -> b.queryParam(k, val));
      } else {
        b.queryParam(k, v);
      }
    });
    return b.build(true).toUri();
  }

  private static URI buildUri(String host, String path) {
    return UriComponentsBuilder.fromHttpUrl(host).path(path).build(true).toUri();
  }

  private static String required(String path, String key) {
    if (path == null || path.isBlank()) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, key);
    }
    return path;
  }

  /**
   * WB REVIEWS могут иметь отдельный host; иначе базовый (sandbox-aware).
   */
  private static String host(MarketplaceProperties.Provider p, MarketplaceType type,
      EndpointKey key) {
    final boolean sandbox = p.isUseSandbox() && p.getSandbox() != null;
    final String base = sandbox ? p.getSandbox().getBaseUrl() : p.getBaseUrl();
    if (type == MarketplaceType.WILDBERRIES && key == EndpointKey.REVIEWS) {
      final String fb = sandbox ? p.getSandbox().getFeedbacksBaseUrl() : p.getFeedbacksBaseUrl();
      return (fb == null || fb.isBlank()) ? base : fb;
    }
    return base;
  }
}
