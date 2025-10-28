package io.datapulse.adapter.marketplaces.http;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.exception.AppException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class HttpHeaderProvider {

  private static final String USER_AGENT = "Datapulse/1.0";

  private final Map<MarketplaceType, HeaderStrategy> strategies;

  public HttpHeaderProvider(List<HeaderStrategy> strategies) {
    var map = new EnumMap<MarketplaceType, HeaderStrategy>(MarketplaceType.class);
    for (HeaderStrategy strategy : strategies) {
      map.put(strategy.type(), strategy);
    }
    this.strategies = Map.copyOf(map);
  }

  public HttpHeaders build(@NonNull MarketplaceType type, MarketplaceCredentials credentials) {
    HeaderStrategy strategy = strategies.get(type);
    if (strategy == null) {
      throw new AppException(MessageCodes.UNKNOWN_MARKETPLACE, type);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
    headers.set(HttpHeaders.USER_AGENT, USER_AGENT);

    strategy.apply(headers, credentials);
    return headers;
  }

  public interface HeaderStrategy {

    MarketplaceType type();

    void apply(HttpHeaders headers, MarketplaceCredentials credentials);
  }

  @Component
  static class WbHeaderStrategy implements HeaderStrategy {

    @Override
    public MarketplaceType type() {
      return MarketplaceType.WILDBERRIES;
    }

    @Override
    public void apply(HttpHeaders headers, MarketplaceCredentials credentials) {
      if (!(credentials instanceof WbCredentials wb) || isBlank(wb.token())) {
        throw new AppException(MessageCodes.WB_MISSING_TOKEN);
      }
      headers.set(HttpHeaders.AUTHORIZATION, wb.token());
    }
  }

  @Component
  static class OzonHeaderStrategy implements HeaderStrategy {

    private static final String HEADER_CLIENT_ID = "Client-Id";
    private static final String HEADER_API_KEY = "Api-Key";

    @Override
    public MarketplaceType type() {
      return MarketplaceType.OZON;
    }

    @Override
    public void apply(HttpHeaders headers, MarketplaceCredentials credentials) {
      if (!(credentials instanceof OzonCredentials ozon)
          || isBlank(ozon.clientId()) || isBlank(ozon.apiKey())) {
        throw new AppException(MessageCodes.OZON_MISSING_CREDENTIALS);
      }
      headers.set(HEADER_CLIENT_ID, ozon.clientId());
      headers.set(HEADER_API_KEY, ozon.apiKey());
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
