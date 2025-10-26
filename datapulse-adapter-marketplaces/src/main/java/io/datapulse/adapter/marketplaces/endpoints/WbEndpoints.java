package io.datapulse.adapter.marketplaces.endpoints;

import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.web.util.UriComponentsBuilder;

public final class WbEndpoints {

  private WbEndpoints() {
  }

  public static URI sales(MarketplaceProperties.Provider provider, LocalDate from, LocalDate to) {
    String base = ensureBaseUrlPresent(provider);
    String path = ensureEndpointConfigured(provider.getEndpoints().getSales(), "sales");
    return UriComponentsBuilder.fromHttpUrl(base)
        .path(path)
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true)
        .toUri();
  }

  public static URI stock(MarketplaceProperties.Provider provider, LocalDate onDate) {
    String base = ensureBaseUrlPresent(provider);
    String path = ensureEndpointConfigured(provider.getEndpoints().getStock(), "stock");
    return UriComponentsBuilder.fromHttpUrl(base)
        .path(path)
        .queryParam("date", onDate)
        .build(true)
        .toUri();
  }

  public static URI finance(MarketplaceProperties.Provider provider, LocalDate from, LocalDate to) {
    String base = ensureBaseUrlPresent(provider);
    String path = ensureEndpointConfigured(provider.getEndpoints().getFinance(), "finance");
    return UriComponentsBuilder.fromHttpUrl(base)
        .path(path)
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true)
        .toUri();
  }

  public static URI reviews(MarketplaceProperties.Provider provider, LocalDate from, LocalDate to) {
    String base = ensureBaseUrlPresent(provider);
    String path = ensureEndpointConfigured(provider.getEndpoints().getReviews(), "reviews");
    return UriComponentsBuilder.fromHttpUrl(base)
        .path(path)
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true)
        .toUri();
  }

  private static String ensureBaseUrlPresent(MarketplaceProperties.Provider provider) {
    String base = provider.getBaseUrl();
    if (base == null || base.isBlank()) {
      throw new AppException(MessageCodes.MARKETPLACE_BASEURL_MISSING,
          MarketplaceType.WILDBERRIES.name());
    }
    return base;
  }

  private static String ensureEndpointConfigured(String value, String endpointCode) {
    if (value == null || value.isBlank()) {
      throw new AppException(MessageCodes.MARKETPLACE_CONFIG_MISSING, endpointCode);
    }
    return value;
  }
}
