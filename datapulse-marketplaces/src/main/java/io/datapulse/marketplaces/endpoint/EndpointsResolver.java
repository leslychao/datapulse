package io.datapulse.marketplaces.endpoint;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.event.BusinessEvent;
import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public final class EndpointsResolver {

  private final Map<MarketplaceType, MarketplaceProperties.Provider> providers;

  /**
   * 1→N сопоставление событий и ключей (по умолчанию).
   */
  private static final Map<BusinessEvent, List<EndpointKey>> DEFAULT_KEYS = defaultKeys();

  public EndpointsResolver(@NonNull MarketplaceProperties props) {
    this.providers = props.getProviders();
  }

  /**
   * Все эндпоинты для события (без query).
   */
  public List<EndpointRef> resolveAll(@NonNull MarketplaceType type, @NonNull BusinessEvent event) {
    var keys = keys(event);
    var p = provider(type);
    return keys.stream()
        .map(k -> new EndpointRef(
            k,
            buildUri(host(p, type, k), required(pathByKey(p, k), k.name().toLowerCase()))
        ))
        .toList();
  }

  /**
   * То же, c едиными query из Map<String, ?>.
   */
  public List<EndpointRef> resolveAll(
      @NonNull MarketplaceType type,
      @NonNull BusinessEvent event,
      @NonNull Map<String, ?> query
  ) {
    return resolveAll(type, event, toQueryParams(query));
  }

  /**
   * То же, c MultiValueMap.
   */
  public List<EndpointRef> resolveAll(
      @NonNull MarketplaceType type,
      @NonNull BusinessEvent event,
      @NonNull MultiValueMap<String, String> query
  ) {
    return resolveAll(type, event).stream()
        .map(ref -> new EndpointRef(ref.key(), applyQuery(ref.uri(), query)))
        .toList();
  }

  // ——— helpers ———

  private static Map<BusinessEvent, List<EndpointKey>> defaultKeys() {
    var m = new EnumMap<BusinessEvent, List<EndpointKey>>(BusinessEvent.class);
    m.put(BusinessEvent.SALES_FACT, List.of(EndpointKey.SALES));
    m.put(BusinessEvent.STOCK_LEVEL, List.of(EndpointKey.STOCK));
    m.put(BusinessEvent.REVIEW, List.of(EndpointKey.REVIEWS));
    m.put(BusinessEvent.RETURN, List.of(EndpointKey.FINANCE));
    m.put(BusinessEvent.AD_PERFORMANCE, List.of(EndpointKey.FINANCE));
    m.put(BusinessEvent.ORDER_POSTING, List.of(EndpointKey.FINANCE));
    m.put(BusinessEvent.PRICE_SNAPSHOT, List.of(EndpointKey.FINANCE));
    m.put(BusinessEvent.CATALOG_ITEM, List.of(EndpointKey.FINANCE));
    return Map.copyOf(m);
  }

  private static List<EndpointKey> keys(BusinessEvent event) {
    var list = DEFAULT_KEYS.get(event);
    if (CollectionUtils.isEmpty(list)) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, "endpoint-keys:" + event);
    }
    return list;
  }

  private MarketplaceProperties.Provider provider(MarketplaceType type) {
    var p = providers.get(type);
    if (p == null || p.getEndpoints() == null) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, type.name());
    }
    if (!StringUtils.hasText(p.getBaseUrl())) {
      throw new AppException(MessageCodes.MARKETPLACE_BASE_URL_MISSING, type.name());
    }
    return p;
  }

  private static String pathByKey(MarketplaceProperties.Provider p, EndpointKey key) {
    return switch (key) {
      case SALES -> p.getEndpoints().getSales();
      case STOCK -> p.getEndpoints().getStock();
      case FINANCE -> p.getEndpoints().getFinance();
      case REVIEWS -> p.getEndpoints().getReviews();
    };
  }

  private static URI applyQuery(URI base, MultiValueMap<String, String> query) {
    return UriComponentsBuilder.fromUri(base)
        .queryParams(query)
        .build(true)
        .toUri();
  }

  private static MultiValueMap<String, String> toQueryParams(Map<String, ?> raw) {
    var mv = new LinkedMultiValueMap<String, String>(raw.size());
    raw.forEach((k, v) -> {
      if (v == null) {
        return;
      }
      if (v instanceof Iterable<?> it) {
        for (Object val : it) {
          if (val != null) {
            mv.add(k, String.valueOf(val));
          }
        }
      } else {
        mv.add(k, String.valueOf(v));
      }
    });
    return mv;
  }

  private static URI buildUri(String host, String path) {
    return UriComponentsBuilder.fromHttpUrl(host).path(path).build(true).toUri();
  }

  private static String required(String path, String key) {
    if (!StringUtils.hasText(path)) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, key);
    }
    return path;
  }

  /**
   * Для WB отзывы могут идти через отдельный host; иначе — базовый (учитываем sandbox).
   */
  private static String host(MarketplaceProperties.Provider p, MarketplaceType type,
      EndpointKey key) {
    final boolean sandbox = p.isUseSandbox() && p.getSandbox() != null;
    final String base = sandbox ? p.getSandbox().getBaseUrl() : p.getBaseUrl();
    if (type == MarketplaceType.WILDBERRIES && key == EndpointKey.REVIEWS) {
      final String fb = sandbox ? p.getSandbox().getFeedbacksBaseUrl() : p.getFeedbacksBaseUrl();
      return StringUtils.hasText(fb) ? fb : base;
    }
    return base;
  }
}
