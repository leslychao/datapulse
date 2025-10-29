package io.datapulse.marketplaces.endpoint;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.event.BusinessEvent;
import java.net.URI;
import java.util.Map;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Minimal resolver: BusinessEvent → EndpointKey → URI for a given marketplace. No extra
 * registries/config classes; single method to resolve everything.
 */
@Component
public final class EndpointsResolver {

  private final Map<MarketplaceType, MarketplaceProperties.Provider> providers;

  public EndpointsResolver(@NonNull MarketplaceProperties properties) {
    this.providers = properties.getProviders();
  }

  /**
   * Single entrypoint: resolve endpoint for marketplace + business event.
   */
  public EndpointRef resolve(
      @NonNull MarketplaceType type,
      @NonNull BusinessEvent event) {
    var key = mapEventToKey(event);
    var p = provider(type);

    String path = switch (key) {
      case SALES -> required(p.getEndpoints().getSales(), "sales");
      case STOCK -> required(p.getEndpoints().getStock(), "stock");
      case FINANCE -> required(p.getEndpoints().getFinance(), "finance");
      case REVIEWS -> required(p.getEndpoints().getReviews(), "reviews");
    };

    String base = host(p, type, key);
    return new EndpointRef(key, buildUri(base, path));
  }

  // ——— mapping ———
  private static EndpointKey mapEventToKey(BusinessEvent event) {
    return switch (event) {
      case SALES_FACT -> EndpointKey.SALES;
      case STOCK_LEVEL -> EndpointKey.STOCK;
      case REVIEW -> EndpointKey.REVIEWS;
      case RETURN -> EndpointKey.FINANCE; // until a dedicated returns API appears
      case AD_PERFORMANCE -> EndpointKey.FINANCE; // temporary
      case ORDER_POSTING -> EndpointKey.FINANCE; // adjust when posting endpoint is added
      case PRICE_SNAPSHOT -> EndpointKey.FINANCE; // adjust when price endpoint is added
      case CATALOG_ITEM -> EndpointKey.FINANCE; // adjust when catalog endpoint is added
    };
  }

  // ——— infra ———
  private MarketplaceProperties.Provider provider(MarketplaceType type) {
    var p = providers.get(type);
    if (p == null || p.getEndpoints() == null) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, type.name());
    }
    if (isBlank(p.getBaseUrl())) {
      throw new AppException(MessageCodes.MARKETPLACE_BASE_URL_MISSING, type.name());
    }
    return p;
  }

  private static URI buildUri(String host, String path) {
    return UriComponentsBuilder.fromHttpUrl(host).path(path).build(true).toUri();
  }

  private static String required(String path, String key) {
    if (isBlank(path)) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, key);
    }
    return path;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /**
   * WB reviews may use a dedicated host; otherwise use base URL (sandbox-aware).
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
