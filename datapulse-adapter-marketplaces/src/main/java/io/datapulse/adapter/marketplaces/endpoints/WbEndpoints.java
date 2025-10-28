package io.datapulse.adapter.marketplaces.endpoints;

import io.datapulse.core.config.MarketplaceProperties;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.net.URI;
import java.time.LocalDate;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WbEndpoints {

  private final String baseUrl;
  private final MarketplaceProperties.Endpoints endpoints;

  public WbEndpoints(@NonNull MarketplaceProperties properties) {
    var provider = properties.get(MarketplaceType.WILDBERRIES);
    if (provider == null) {
      throw new AppException(
          MessageCodes.MARKETPLACE_CONFIG_MISSING,
          MarketplaceType.WILDBERRIES.name()
      );
    }
    if (provider.getEndpoints() == null) {
      throw new AppException(
          MessageCodes.MARKETPLACE_ENDPOINTS_MISSING,
          MarketplaceType.WILDBERRIES.name()
      );
    }

    this.baseUrl = ensureBaseUrlPresent(provider);
    this.endpoints = provider.getEndpoints();
  }

  public URI sales(LocalDate from, LocalDate to) {
    requireRange(from, to);
    String path = ensureEndpointConfigured(endpoints.getSales(), "sales");
    return UriComponentsBuilder.fromHttpUrl(baseUrl)
        .path(path)
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true)
        .toUri();
  }

  public URI stock(LocalDate onDate) {
    requireNonNull(onDate, "date");
    String path = ensureEndpointConfigured(endpoints.getStock(), "stock");
    return UriComponentsBuilder.fromHttpUrl(baseUrl)
        .path(path)
        .queryParam("date", onDate)
        .build(true)
        .toUri();
  }

  public URI finance(LocalDate from, LocalDate to) {
    requireRange(from, to);
    String path = ensureEndpointConfigured(endpoints.getFinance(), "finance");
    return UriComponentsBuilder.fromHttpUrl(baseUrl)
        .path(path)
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true)
        .toUri();
  }

  public URI reviews(LocalDate from, LocalDate to) {
    requireRange(from, to);
    String path = ensureEndpointConfigured(endpoints.getReviews(), "reviews");
    return UriComponentsBuilder.fromHttpUrl(baseUrl)
        .path(path)
        .queryParam("dateFrom", from)
        .queryParam("dateTo", to)
        .build(true)
        .toUri();
  }

  private static void requireRange(LocalDate from, LocalDate to) {
    if (from == null) {
      throw new AppException(MessageCodes.REQUIRED_PARAMETER_MISSING, "dateFrom");
    }
    if (to == null) {
      throw new AppException(MessageCodes.REQUIRED_PARAMETER_MISSING, "dateTo");
    }
    if (from.isAfter(to)) {
      throw new AppException(MessageCodes.INVALID_DATE_RANGE, "dateFrom > dateTo");
    }
  }

  private static void requireNonNull(Object value, String paramName) {
    if (value == null) {
      throw new AppException(MessageCodes.REQUIRED_PARAMETER_MISSING, paramName);
    }
  }

  private static String ensureBaseUrlPresent(MarketplaceProperties.Provider provider) {
    String base = provider.getBaseUrl();
    if (base == null || base.isBlank()) {
      throw new AppException(
          MessageCodes.MARKETPLACE_BASE_URL_MISSING,
          MarketplaceType.WILDBERRIES.name()
      );
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
