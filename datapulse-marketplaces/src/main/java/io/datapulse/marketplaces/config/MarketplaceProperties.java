package io.datapulse.marketplaces.config;

import static io.datapulse.domain.MessageCodes.MARKETPLACE_CONFIG_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_BASE_URL_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINT_PATH_REQUIRED;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Минимальная конфигурация:
 * - URL’ы + endpoints.
 * - Resilience: только то, что нужно для простых ретраев (maxAttempts/baseBackoff/maxBackoff/retryAfterFallback).
 * Пер-endpoint override оставлен, но включает только эти поля.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "marketplace")
public class MarketplaceProperties {

  @NotNull
  private final Map<MarketplaceType, @Valid Provider> providers =
      new EnumMap<>(MarketplaceType.class);

  public Provider get(MarketplaceType type) {
    var provider = providers.get(type);
    if (provider == null) {
      throw new AppException(MARKETPLACE_CONFIG_MISSING, type.name());
    }
    return provider;
  }

  @Getter
  @Setter
  @Validated
  public static class Provider {

    private boolean useSandbox;

    @NotBlank(message = MARKETPLACE_BASE_URL_MISSING)
    private String baseUrl;

    private String feedbacksBaseUrl;

    @Valid
    private Sandbox sandbox;

    @NotNull(message = MARKETPLACE_ENDPOINTS_REQUIRED)
    private final Map<EndpointKey, @NotBlank(message = MARKETPLACE_ENDPOINT_PATH_REQUIRED) String>
        endpoints = new EnumMap<>(EndpointKey.class);

    @Valid
    @NotNull
    private Resilience resilience;

    @Valid
    private Map<EndpointKey, ResilienceOverride> resilienceOverrides =
        new EnumMap<>(EndpointKey.class);

    public String endpoint(EndpointKey key) {
      return endpoints.get(key);
    }

    /** Базовый + точечный override только по нужным полям. */
    public Resilience effectiveResilience(EndpointKey key) {
      var base = resilience;
      var ov = (resilienceOverrides != null) ? resilienceOverrides.get(key) : null;
      if (ov == null) return base;
      var r = new Resilience();
      r.maxAttempts = nvl(ov.getMaxAttempts(), base.getMaxAttempts());
      r.baseBackoff = nvl(ov.getBaseBackoff(), base.getBaseBackoff());
      r.maxBackoff = nvl(ov.getMaxBackoff(), base.getMaxBackoff());
      r.retryAfterFallback = nvl(ov.getRetryAfterFallback(), base.getRetryAfterFallback());
      return r;
    }

    private static <T> T nvl(T v, T def) {
      return (v != null) ? v : def;
    }
  }

  @Getter
  @Setter
  @Validated
  public static class Sandbox {
    @NotBlank(message = MARKETPLACE_BASE_URL_MISSING)
    private String baseUrl;
    private String feedbacksBaseUrl;
  }

  @Getter
  @Setter
  @Validated
  public static class Resilience {
    /** Всего попыток, включая первую. */
    @NotNull private Integer maxAttempts;
    @NotNull private Duration baseBackoff;
    @NotNull private Duration maxBackoff;
    /** Если нет заголовков (WB/Ozon), сколько подождать «по умолчанию». Можно 0s. */
    @NotNull private Duration retryAfterFallback;
  }

  @Getter
  @Setter
  public static class ResilienceOverride {
    private Integer maxAttempts;
    private Duration baseBackoff;
    private Duration maxBackoff;
    private Duration retryAfterFallback;
  }
}
