package io.datapulse.marketplaces.config;

import static io.datapulse.domain.MessageCodes.MARKETPLACE_CONFIG_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_BASE_URL_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINT_PATH_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_BASE_BACKOFF_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_BULKHEAD_WAIT_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_LIMIT_FOR_PERIOD_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_LIMIT_REFRESH_PERIOD_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_MAX_ATTEMPTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_MAX_BACKOFF_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_MAX_CONCURRENT_CALLS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_MAX_JITTER_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_RETRY_AFTER_FALLBACK_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RESILIENCE_TOKEN_WAIT_TIMEOUT_REQUIRED;

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

  // ─────────────────────────────────────────────────────────────────────────────

  @Getter
  @Setter
  @Validated
  public static class Provider {

    private boolean useSandbox;

    @NotBlank(message = MARKETPLACE_BASE_URL_MISSING)
    private String baseUrl;

    /** WB: опциональный отдельный host для отзывов. */
    private String feedbacksBaseUrl;

    @Valid
    private Sandbox sandbox;

    /**
     * Карта путей по ключам эндпоинтов (строго через enum-ключи).
     */
    @NotNull(message = MARKETPLACE_ENDPOINTS_REQUIRED)
    private final Map<EndpointKey, @NotBlank(message = MARKETPLACE_ENDPOINT_PATH_REQUIRED) String>
        endpoints = new EnumMap<>(EndpointKey.class);

    /**
     * Базовая (обязательная и полная) резилентность.
     */
    @Valid
    @NotNull(message = MARKETPLACE_RESILIENCE_REQUIRED)
    private Resilience resilience;

    /**
     * Частичные оверрайды (опционально) по ключам эндпоинтов.
     */
    @Valid
    private Map<EndpointKey, ResilienceOverride> resilienceOverrides =
        new EnumMap<>(EndpointKey.class);

    public String endpoint(EndpointKey key) {
      return endpoints.get(key);
    }

    /** Итоговая резилентность = base ⊕ override(key). */
    public Resilience effectiveResilience(EndpointKey key) {
      var base = resilience;
      var ov = (resilienceOverrides != null) ? resilienceOverrides.get(key) : null;
      return base.mergeWithOverride(ov);
    }

    /** Быстрый доступ к maxConcurrentCalls с учётом override. */
    public int effectiveMaxConcurrentCalls(EndpointKey key) {
      var ov = (resilienceOverrides != null) ? resilienceOverrides.get(key) : null;
      return (ov != null && ov.getMaxConcurrentCalls() != null)
          ? ov.getMaxConcurrentCalls()
          : resilience.getMaxConcurrentCalls();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────

  @Getter
  @Setter
  @Validated
  public static class Sandbox {

    @NotBlank(message = MARKETPLACE_BASE_URL_MISSING)
    private String baseUrl;

    /** WB: опциональный отдельный host для отзывов в sandbox. */
    private String feedbacksBaseUrl;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // БАЗА: всё обязательно (@NotNull/Blank) — fail-fast на старте приложения

  @Getter
  @Setter
  @Validated
  public static class Resilience {

    @NotNull(message = MARKETPLACE_RESILIENCE_LIMIT_FOR_PERIOD_REQUIRED)
    private Integer limitForPeriod;

    @NotNull(message = MARKETPLACE_RESILIENCE_MAX_CONCURRENT_CALLS_REQUIRED)
    private Integer maxConcurrentCalls;

    @NotNull(message = MARKETPLACE_RESILIENCE_MAX_ATTEMPTS_REQUIRED)
    private Integer maxAttempts;

    @NotNull(message = MARKETPLACE_RESILIENCE_BASE_BACKOFF_REQUIRED)
    private Duration baseBackoff;

    @NotNull(message = MARKETPLACE_RESILIENCE_MAX_BACKOFF_REQUIRED)
    private Duration maxBackoff;

    @NotNull(message = MARKETPLACE_RESILIENCE_MAX_JITTER_REQUIRED)
    private Duration maxJitter;

    @NotNull(message = MARKETPLACE_RESILIENCE_RETRY_AFTER_FALLBACK_REQUIRED)
    private Duration retryAfterFallback;

    @NotNull(message = MARKETPLACE_RESILIENCE_LIMIT_REFRESH_PERIOD_REQUIRED)
    private Duration limitRefreshPeriod;

    @NotNull(message = MARKETPLACE_RESILIENCE_TOKEN_WAIT_TIMEOUT_REQUIRED)
    private Duration tokenWaitTimeout;

    @NotNull(message = MARKETPLACE_RESILIENCE_BULKHEAD_WAIT_REQUIRED)
    private Duration bulkheadWait;

    /** Слияние: берём base, поверх «накрываем» непустыми полями из override. */
    public Resilience mergeWithOverride(ResilienceOverride ov) {
      if (ov == null) {
        return this;
      }
      var r = new Resilience();
      r.limitForPeriod = nvl(ov.getLimitForPeriod(), this.limitForPeriod);
      r.maxConcurrentCalls = nvl(ov.getMaxConcurrentCalls(), this.maxConcurrentCalls);
      r.maxAttempts = nvl(ov.getMaxAttempts(), this.maxAttempts);
      r.baseBackoff = nvl(ov.getBaseBackoff(), this.baseBackoff);
      r.maxBackoff = nvl(ov.getMaxBackoff(), this.maxBackoff);
      r.maxJitter = nvl(ov.getMaxJitter(), this.maxJitter);
      r.retryAfterFallback = nvl(ov.getRetryAfterFallback(), this.retryAfterFallback);
      r.limitRefreshPeriod = nvl(ov.getLimitRefreshPeriod(), this.limitRefreshPeriod);
      r.tokenWaitTimeout = nvl(ov.getTokenWaitTimeout(), this.tokenWaitTimeout);
      r.bulkheadWait = nvl(ov.getBulkheadWait(), this.bulkheadWait);
      return r;
    }

    private static <T> T nvl(T v, T def) {
      return (v != null) ? v : def;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // OVERRIDE: всё опционально (частичное перекрытие)

  @Getter
  @Setter
  public static class ResilienceOverride {

    private Integer limitForPeriod;
    private Integer maxConcurrentCalls;
    private Integer maxAttempts;
    private Duration baseBackoff;
    private Duration maxBackoff;
    private Duration maxJitter;
    private Duration retryAfterFallback;
    private Duration limitRefreshPeriod;
    private Duration tokenWaitTimeout;
    private Duration bulkheadWait;
  }
}
