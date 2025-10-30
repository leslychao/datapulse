package io.datapulse.marketplaces.endpoint;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.event.BusinessEvent;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public final class EndpointsResolver {

  private final MarketplaceProperties properties;

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

  public List<EndpointRef> resolveAll(@NonNull MarketplaceType type, @NonNull BusinessEvent event) {
    var keys = DEFAULT_KEYS.get(event);
    if (keys == null || keys.isEmpty()) {
      // ⬇️ больше не хардкодим строку — берём константу из MessageCodes
      throw new AppException(MessageCodes.MARKETPLACE_EVENT_ENDPOINTS_MISSING, event.name());
    }
    var provider = properties.get(type);
    return keys.stream()
        .map(k -> new EndpointRef(
            k,
            buildUri(
                host(provider, type, k),
                requiredEndpointPath(provider, k, type)
            )))
        .toList();
  }

  public List<EndpointRef> resolveAll(
      @NonNull MarketplaceType type,
      @NonNull BusinessEvent event,
      @NonNull Map<String, ?> query
  ) {
    return resolveAll(type, event).stream()
        .map(ref -> new EndpointRef(ref.key(), applyQuery(ref.uri(), query)))
        .toList();
  }

  // ── helpers ───────────────────────────────────────────────────────────────────

  private static URI applyQuery(URI base, Map<String, ?> query) {
    var b = UriComponentsBuilder.fromUri(base);
    query.forEach((k, v) -> {
      if (v == null) {
        return;
      }
      if (v.getClass().isArray()) {
        IntStream.range(0, Array.getLength(v))
            .mapToObj(i -> Array.get(v, i))
            .forEach(val -> b.queryParam(k, val));
      } else if (v instanceof Iterable<?> it) {
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

  /**
   * отдельный метод, чтобы использовать корректный код ошибки для отсутствующего path
   */
  private static String requiredEndpointPath(
      MarketplaceProperties.Provider provider,
      EndpointKey key,
      MarketplaceType type
  ) {
    String path = provider.endpoint(key); // см. примечание ниже
    if (path == null || path.isBlank()) {
      // ⬇️ раньше здесь был MARKETPLACE_CONFIG_MISSING, теперь — точный код
      throw new AppException(MessageCodes.MARKETPLACE_ENDPOINT_PATH_MISSING,
          key.name(),
          type.name());
    }
    return path;
  }

  private static String host(
      MarketplaceProperties.Provider provider,
      MarketplaceType marketplaceType,
      EndpointKey endpointKey
  ) {
    final boolean sandbox = provider.isUseSandbox() && provider.getSandbox() != null;
    final String baseUrl = sandbox ? provider.getSandbox().getBaseUrl() : provider.getBaseUrl();

    if (baseUrl == null || baseUrl.isBlank()) {
      throw new AppException(MessageCodes.MARKETPLACE_BASE_URL_MISSING, marketplaceType.name());
    }

    if (marketplaceType == MarketplaceType.WILDBERRIES && endpointKey == EndpointKey.REVIEWS) {
      final String feedbacksBaseUrl =
          sandbox ? provider.getSandbox().getFeedbacksBaseUrl() : provider.getFeedbacksBaseUrl();
      return (feedbacksBaseUrl == null || feedbacksBaseUrl.isBlank()) ? baseUrl : feedbacksBaseUrl;
    }
    return baseUrl;
  }
}
