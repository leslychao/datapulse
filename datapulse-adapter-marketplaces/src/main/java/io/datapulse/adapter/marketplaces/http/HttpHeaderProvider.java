package io.datapulse.adapter.marketplaces.http;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.exception.AppException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class HttpHeaderProvider {

  private static final Map<MarketplaceType, HeaderStrategy> STRATEGIES =
      new EnumMap<>(MarketplaceType.class);

  static {
    STRATEGIES.put(MarketplaceType.WILDBERRIES, new WbHeaderStrategy());
    STRATEGIES.put(MarketplaceType.OZON, new OzonHeaderStrategy());
  }

  public HttpHeaders build(MarketplaceType type, MarketplaceCredentials credentials) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(MediaType.parseMediaTypes(MediaType.APPLICATION_JSON_VALUE));
    headers.set(HttpHeaders.USER_AGENT, "Datapulse/1.0");

    HeaderStrategy strategy = STRATEGIES.get(type);
    if (strategy == null) {
      throw new AppException(MessageCodes.HTTP_HEADERS_UNKNOWN_MARKETPLACE, type);
    }

    strategy.apply(headers, credentials);
    return headers;
  }

  interface HeaderStrategy {

    void apply(HttpHeaders headers, MarketplaceCredentials credentials);
  }

  private static class WbHeaderStrategy implements HeaderStrategy {

    @Override
    public void apply(HttpHeaders headers, MarketplaceCredentials credentials) {
      if (credentials instanceof WbCredentials wb && wb.token() != null && !wb.token().isBlank()) {
        headers.set(HttpHeaders.AUTHORIZATION, wb.token());
      } else {
        throw new AppException(MessageCodes.HTTP_HEADERS_WB_MISSING_TOKEN);
      }
    }
  }

  private static class OzonHeaderStrategy implements HeaderStrategy {

    private static final String HEADER_CLIENT_ID = "Client-Id";
    private static final String HEADER_API_KEY = "Api-Key";

    @Override
    public void apply(HttpHeaders headers, MarketplaceCredentials credentials) {
      if (credentials instanceof OzonCredentials ozon
          && ozon.clientId() != null && !ozon.clientId().isBlank()
          && ozon.apiKey() != null && !ozon.apiKey().isBlank()) {
        headers.set(HEADER_CLIENT_ID, ozon.clientId());
        headers.set(HEADER_API_KEY, ozon.apiKey());
      } else {
        throw new AppException(MessageCodes.HTTP_HEADERS_OZON_MISSING_CREDENTIALS);
      }
    }
  }
}
