package io.datapulse.marketplaces.endpoints;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import java.net.URI;
import java.util.Map;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EndpointsResolver {

  private final Map<MarketplaceType, MarketplaceProperties.Provider> providers;

  public EndpointsResolver(@NonNull MarketplaceProperties properties) {
    this.providers = properties.getProviders();
  }

  public EndpointRef salesRef(@NonNull MarketplaceType type) {
    return new EndpointRef(EndpointKey.SALES, sales(type));
  }

  public EndpointRef stockRef(@NonNull MarketplaceType type) {
    return new EndpointRef(EndpointKey.STOCK, stock(type));
  }

  public EndpointRef financeRef(@NonNull MarketplaceType type) {
    return new EndpointRef(EndpointKey.FINANCE, finance(type));
  }

  public EndpointRef reviewsRef(@NonNull MarketplaceType type) {
    return new EndpointRef(EndpointKey.REVIEWS, reviews(type));
  }

  // Backward-compat (если где-то используются)
  public URI sales(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(host(p, type, EndpointKey.SALES),
        required(p.getEndpoints().getSales(), "sales"));
  }

  public URI stock(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(host(p, type, EndpointKey.STOCK),
        required(p.getEndpoints().getStock(), "stock"));
  }

  public URI finance(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(host(p, type, EndpointKey.FINANCE),
        required(p.getEndpoints().getFinance(), "finance"));
  }

  public URI reviews(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(host(p, type, EndpointKey.REVIEWS),
        required(p.getEndpoints().getReviews(), "reviews"));
  }

  // ——— private ———
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

  private static String host(MarketplaceProperties.Provider p, MarketplaceType type,
      EndpointKey endpointKey) {
    final boolean sandbox = p.isUseSandbox() && p.getSandbox() != null;
    final String base = sandbox ? p.getSandbox().getBaseUrl() : p.getBaseUrl();
    if (type == MarketplaceType.WILDBERRIES && endpointKey == EndpointKey.REVIEWS) {
      final String fb = sandbox ? p.getSandbox().getFeedbacksBaseUrl()
          : p.getFeedbacksBaseUrl();
      return (fb == null || fb.isBlank()) ? base : fb;
    }
    return base;
  }
}
