package io.datapulse.marketplaces.endpoint;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public final class EndpointsResolver {

  private final MarketplaceProperties properties;

  public EndpointsResolver(@NonNull MarketplaceProperties properties) {
    this.properties = properties;
  }

  public EndpointRef resolve(@NonNull MarketplaceType type, @NonNull EndpointKey key) {
    var provider = properties.get(type);
    var host = resolveHost(provider, type, key);
    var path = resolveRequiredPath(provider, key);
    return new EndpointRef(key, buildUri(host, path));
  }

  public EndpointRef resolve(
      @NonNull MarketplaceType type, @NonNull EndpointKey key, @NonNull Map<String, ?> query
  ) {
    var base = resolve(type, key);
    return new EndpointRef(key, applyQuery(base.uri(), query));
  }

  private static URI buildUri(String host, String path) {
    return UriComponentsBuilder.fromHttpUrl(host).path(path).build(true).toUri();
  }

  private static URI applyQuery(URI base, Map<String, ?> query) {
    var uriComponentsBuilder = UriComponentsBuilder.fromUri(base);
    query.forEach((k, v) -> {
      if (v == null) {
        return;
      }
      if (v.getClass().isArray()) {
        IntStream.range(0, Array.getLength(v)).mapToObj(i -> Array.get(v, i))
            .forEach(val -> uriComponentsBuilder.queryParam(k, val));
      } else if (v instanceof Iterable<?> it) {
        it.forEach(val -> uriComponentsBuilder.queryParam(k, val));
      } else {
        uriComponentsBuilder.queryParam(k, v);
      }
    });
    return uriComponentsBuilder.build(true).toUri();
  }

  private static String resolveRequiredPath(
      MarketplaceProperties.Provider provider,
      EndpointKey key
  ) {
    return provider.endpoint(key);
  }

  private static String resolveHost(
      MarketplaceProperties.Provider provider,
      MarketplaceType type,
      EndpointKey key
  ) {
    final boolean sandbox = provider.isUseSandbox() && provider.getSandbox() != null;
    final String baseUrl = sandbox ? provider.getSandbox().getBaseUrl() : provider.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new AppException(MessageCodes.MARKETPLACE_BASE_URL_MISSING, type.name());
    }
    if (type == MarketplaceType.WILDBERRIES && key == EndpointKey.REVIEWS) {
      final String feedbackBaseUrl =
          sandbox ? provider.getSandbox().getFeedbacksBaseUrl() : provider.getFeedbacksBaseUrl();
      return (feedbackBaseUrl == null || feedbackBaseUrl.isBlank()) ? baseUrl : feedbackBaseUrl;
    }
    return baseUrl;
  }
}
