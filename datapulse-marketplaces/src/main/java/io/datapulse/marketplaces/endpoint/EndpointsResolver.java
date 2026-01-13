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
    var endpointConfig = provider.endpointConfig(key);
    var uri = resolveUri(endpointConfig, type);
    return new EndpointRef(key, uri);
  }

  public EndpointRef resolve(
      @NonNull MarketplaceType type,
      @NonNull EndpointKey key,
      @NonNull Map<String, ?> query
  ) {
    var base = resolve(type, key);
    return new EndpointRef(key, applyQuery(base.uri(), query));
  }

  private static URI resolveUri(
      MarketplaceProperties.EndpointConfig endpointConfig,
      MarketplaceType type
  ) {
    final String url = endpointConfig.getUrl();

    if (url == null || url.isBlank()) {
      throw new AppException(MessageCodes.MARKETPLACE_BASE_URL_MISSING, type.name());
    }

    return UriComponentsBuilder.fromHttpUrl(url).build(true).toUri();
  }

  private static URI applyQuery(URI base, Map<String, ?> query) {
    var uriComponentsBuilder = UriComponentsBuilder.fromUri(base);

    query.forEach((key, value) -> {
      if (value == null) {
        return;
      }
      if (value.getClass().isArray()) {
        IntStream.range(0, Array.getLength(value))
            .mapToObj(index -> Array.get(value, index))
            .forEach(element -> uriComponentsBuilder.queryParam(key, element));
      } else if (value instanceof Iterable<?> iterable) {
        iterable.forEach(element -> uriComponentsBuilder.queryParam(key, element));
      } else {
        uriComponentsBuilder.queryParam(key, value);
      }
    });

    return uriComponentsBuilder.build(true).toUri();
  }
}
