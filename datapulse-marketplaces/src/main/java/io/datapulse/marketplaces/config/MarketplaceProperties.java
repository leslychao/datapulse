package io.datapulse.marketplaces.config;

import static io.datapulse.domain.MessageCodes.MARKETPLACE_CONFIG_MISSING;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
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
    Provider provider = providers.get(type);
    if (provider == null) {
      throw new AppException(MARKETPLACE_CONFIG_MISSING, type);
    }
    return provider;
  }

  @Getter
  @Setter
  @Validated
  public static class Provider {

    /**
     * Переключатель режима песочницы на уровне провайдера.
     */
    private boolean useSandbox = false;

    @NotBlank
    private String baseUrl;

    private String feedbacksBaseUrl;

    @Valid
    private Sandbox sandbox;

    @Valid
    @NotNull
    private Endpoints endpoints;

    @Valid
    @NotNull
    private Resilience resilience;

    /**
     * Опциональные локальные оверрайды параметров резилентности по ключу эндпоинта: sales | stock |
     * finance | reviews.
     */
    @Valid
    private Map<String, Resilience> resilienceOverrides;
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
  public static class Endpoints {

    @NotBlank
    private String sales;
    @NotBlank
    private String stock;
    @NotBlank
    private String finance;
    @NotBlank
    private String reviews;
  }

  @Getter
  @Setter
  @Validated
  public static class Resilience {

    @NotNull
    private Integer limitForPeriod;
    @NotNull
    private Integer maxConcurrentCalls;
    @NotNull
    private Integer maxAttempts;
    @NotNull
    private Duration baseBackoff;
    @NotNull
    private Duration maxBackoff;
    @NotNull
    private Duration maxJitter;           // 0 — без джиттера
    @NotNull
    private Duration retryAfterFallback;  // 0 — без ожидания по умолчанию
    @NotNull
    private Duration limitRefreshPeriod;  // rate limiter refresh
    @NotNull
    private Duration tokenWaitTimeout;    // rate limiter acquire timeout
    @NotNull
    private Duration bulkheadWait;        // bulkhead max wait
  }
}
