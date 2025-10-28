package io.datapulse.marketplaces.endpoints;

import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
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

  public URI sales(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(p.getBaseUrl(), nonBlank(p.getEndpoints().getSales()));
  }

  public URI stock(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(p.getBaseUrl(), nonBlank(p.getEndpoints().getStock()));
  }

  public URI finance(@NonNull MarketplaceType type) {
    var p = provider(type);
    return buildUri(p.getBaseUrl(), nonBlank(p.getEndpoints().getFinance()));
  }

  public URI reviews(@NonNull MarketplaceType type) {
    var p = provider(type);
    String host = p.getBaseUrl();
    if (type == MarketplaceType.WILDBERRIES && notBlank(p.getFeedbacksBaseUrl())) {
      host = p.getFeedbacksBaseUrl();
    }
    return buildUri(host, nonBlank(p.getEndpoints().getReviews()));
  }

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

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static boolean notBlank(String s) {
    return !isBlank(s);
  }

  private static String nonBlank(String path) {
    if (isBlank(path)) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, path);
    }
    return path;
  }
}
