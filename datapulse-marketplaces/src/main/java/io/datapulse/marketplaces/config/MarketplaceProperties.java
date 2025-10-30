package io.datapulse.marketplaces.config;

import static io.datapulse.domain.MessageCodes.MARKETPLACE_CONFIG_MISSING;

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
  private final Map<MarketplaceType, @Valid Provider> providers = new EnumMap<>(
      MarketplaceType.class);

  public Provider get(MarketplaceType type) {
    var provider = providers.get(type);
    if (provider == null) {
      throw new AppException(MARKETPLACE_CONFIG_MISSING, type);
    }
    return provider;
  }

  @Getter
  @Setter
  @Validated
  public static class Provider {

    private boolean useSandbox;

    @NotBlank
    private String baseUrl;
    private String feedbacksBaseUrl;

    @Valid
    private Sandbox sandbox;

    @NotNull
    private final Map<EndpointKey, @NotBlank String> endpoints = new EnumMap<>(EndpointKey.class);

    @Valid
    @NotNull
    private Resilience resilience;

    @Valid
    private Map<EndpointKey, Resilience> resilienceOverrides = new EnumMap<>(EndpointKey.class);

    public String endpoint(EndpointKey key) {
      return endpoints.get(key);
    }

    public Resilience effectiveResilience(EndpointKey key) {
      var base = resilience.requireAll();
      var override = resilienceOverrides.get(key);
      return (override == null) ? base : base.mergeWith(override);
    }

    public int effectiveMaxConcurrentCalls(EndpointKey key) {
      var o = resilienceOverrides.get(key);
      return (o != null && o.getMaxConcurrentCalls() != null)
          ? o.getMaxConcurrentCalls()
          : resilience.requireAll().getMaxConcurrentCalls();
    }
  }

  @Getter
  @Setter
  @Validated
  public static class Sandbox {

    @NotBlank
    private String baseUrl;
    private String feedbacksBaseUrl;
  }

  @Getter
  @Setter
  @Validated
  public static class Resilience {

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

    public Resilience mergeWith(Resilience o) {
      var resilience = new Resilience();
      resilience.limitForPeriod = nvl(o.limitForPeriod, this.limitForPeriod);
      resilience.maxConcurrentCalls = nvl(o.maxConcurrentCalls, this.maxConcurrentCalls);
      resilience.maxAttempts = nvl(o.maxAttempts, this.maxAttempts);
      resilience.baseBackoff = nvl(o.baseBackoff, this.baseBackoff);
      resilience.maxBackoff = nvl(o.maxBackoff, this.maxBackoff);
      resilience.maxJitter = nvl(o.maxJitter, this.maxJitter);
      resilience.retryAfterFallback = nvl(o.retryAfterFallback, this.retryAfterFallback);
      resilience.limitRefreshPeriod = nvl(o.limitRefreshPeriod, this.limitRefreshPeriod);
      resilience.tokenWaitTimeout = nvl(o.tokenWaitTimeout, this.tokenWaitTimeout);
      resilience.bulkheadWait = nvl(o.bulkheadWait, this.bulkheadWait);
      return resilience;
    }

    /**
     * проверка полноты «базы»
     */
    public Resilience requireAll() {
      if (limitForPeriod == null ||
          maxConcurrentCalls == null ||
          maxAttempts == null ||
          baseBackoff == null ||
          maxBackoff == null ||
          maxJitter == null ||
          retryAfterFallback == null ||
          limitRefreshPeriod == null ||
          tokenWaitTimeout == null ||
          bulkheadWait == null) {
        throw new AppException("MARKETPLACE_RESILIENCE_INCOMPLETE");
      }
      return this;
    }

    private static <T> T nvl(T v, T def) {
      return (v != null) ? v : def;
    }
  }
}
