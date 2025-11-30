package io.datapulse.marketplaces.config;

import static io.datapulse.domain.ValidationKeys.MARKETPLACE_BASE_URL_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_PROVIDERS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_STORAGE_BASEDIR_REQUIRED;

import static io.datapulse.domain.MessageCodes.MARKETPLACE_CONFIG_MISSING;
import static io.datapulse.domain.MessageCodes.MARKETPLACE_ENDPOINT_PATH_REQUIRED;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
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

  @Valid
  private Storage storage;

  @NotNull(message = MARKETPLACE_PROVIDERS_REQUIRED)
  private final Map<MarketplaceType, @Valid Provider> providers =
      new EnumMap<>(MarketplaceType.class);

  public Provider get(MarketplaceType marketplaceType) {
    Provider providerConfig = providers.get(marketplaceType);
    if (providerConfig == null) {
      throw new AppException(MARKETPLACE_CONFIG_MISSING, marketplaceType.name());
    }
    return providerConfig;
  }

  @Getter
  @Setter
  public static class Storage {

    @NotNull(message = MARKETPLACE_STORAGE_BASEDIR_REQUIRED)
    private Path baseDir;
  }

  @Getter
  @Setter
  @Validated
  public static class Provider {

    @NotNull(message = MARKETPLACE_ENDPOINTS_REQUIRED)
    private final Map<EndpointKey, @Valid EndpointConfig> endpoints =
        new EnumMap<>(EndpointKey.class);

    @Valid
    @NotNull(message = MARKETPLACE_RETRY_POLICY_REQUIRED)
    private RetryPolicy retryPolicy;

    public EndpointConfig endpointConfig(EndpointKey endpointKey) {
      EndpointConfig cfg = endpoints.get(endpointKey);
      if (cfg == null || cfg.getUrl() == null || cfg.getUrl().isBlank()) {
        throw new AppException(MARKETPLACE_ENDPOINT_PATH_REQUIRED, endpointKey.name());
      }
      return cfg;
    }

    public RetryPolicy effectiveRetryPolicy(EndpointKey endpointKey) {
      if (retryPolicy == null) {
        throw new AppException(MARKETPLACE_RETRY_POLICY_REQUIRED);
      }

      EndpointConfig endpointConfig = endpoints.get(endpointKey);
      if (endpointConfig == null || endpointConfig.getRetryPolicyOverride() == null) {
        return retryPolicy;
      }

      RetryPolicyOverride overrideConfig = endpointConfig.getRetryPolicyOverride();

      RetryPolicy mergedRetryPolicy = new RetryPolicy();
      mergedRetryPolicy.maxAttempts =
          chooseNotNull(overrideConfig.getMaxAttempts(), retryPolicy.getMaxAttempts());
      mergedRetryPolicy.baseBackoff =
          chooseNotNull(overrideConfig.getBaseBackoff(), retryPolicy.getBaseBackoff());
      mergedRetryPolicy.maxBackoff =
          chooseNotNull(overrideConfig.getMaxBackoff(), retryPolicy.getMaxBackoff());

      return mergedRetryPolicy;
    }

    private static <T> T chooseNotNull(T value, T defaultValue) {
      return (value != null) ? value : defaultValue;
    }
  }

  @Getter
  @Setter
  public static class EndpointConfig {

    @NotBlank(message = MARKETPLACE_BASE_URL_MISSING)
    private String url;

    private RetryPolicyOverride retryPolicyOverride;
  }

  @Getter
  @Setter
  public static class RetryPolicy {

    @NotNull(message = MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED)
    private Integer maxAttempts;

    @NotNull(message = MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED)
    private Duration baseBackoff;

    @NotNull(message = MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED)
    private Duration maxBackoff;
  }

  @Getter
  @Setter
  public static class RetryPolicyOverride {
    private Integer maxAttempts;
    private Duration baseBackoff;
    private Duration maxBackoff;
  }
}
