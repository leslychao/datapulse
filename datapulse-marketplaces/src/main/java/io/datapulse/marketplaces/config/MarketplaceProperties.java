package io.datapulse.marketplaces.config;

import static io.datapulse.domain.MessageCodes.MARKETPLACE_CONFIG_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_BASE_URL_MISSING;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_ENDPOINT_PATH_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_REQUIRED;
import static io.datapulse.domain.ValidationKeys.MARKETPLACE_RETRY_POLICY_RETRY_AFTER_FALLBACK_REQUIRED;

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

  @NotNull
  private final Map<MarketplaceType, @Valid Provider> providers =
      new EnumMap<>(MarketplaceType.class);

  public Provider get(MarketplaceType marketplaceType) {
    var providerConfig = providers.get(marketplaceType);
    if (providerConfig == null) {
      throw new AppException(MARKETPLACE_CONFIG_MISSING, marketplaceType.name());
    }
    providerConfig.validateSandboxConfig();
    return providerConfig;
  }

  @Getter
  @Setter
  public static class Storage {
    @NotNull
    private Path baseDir;
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
    @NotNull(message = MARKETPLACE_RETRY_POLICY_REQUIRED)
    private RetryPolicy retryPolicy;

    private Map<EndpointKey, RetryPolicyOverride> retryPolicyOverrides =
        new EnumMap<>(EndpointKey.class);

    public String endpoint(EndpointKey endpointKey) {
      var endpointPath = endpoints.get(endpointKey);
      if (endpointPath == null || endpointPath.isBlank()) {
        throw new AppException(MARKETPLACE_ENDPOINT_PATH_REQUIRED, endpointKey.name());
      }
      return endpointPath;
    }

    public RetryPolicy effectiveRetryPolicy(EndpointKey endpointKey) {
      if (retryPolicy == null) {
        throw new AppException(MARKETPLACE_RETRY_POLICY_REQUIRED);
      }

      var overrideConfig = retryPolicyOverrides.get(endpointKey);
      if (overrideConfig == null) {
        return retryPolicy;
      }

      var mergedRetryPolicy = new RetryPolicy();
      mergedRetryPolicy.maxAttempts =
          chooseNotNull(overrideConfig.getMaxAttempts(), retryPolicy.getMaxAttempts());
      mergedRetryPolicy.baseBackoff =
          chooseNotNull(overrideConfig.getBaseBackoff(), retryPolicy.getBaseBackoff());
      mergedRetryPolicy.maxBackoff =
          chooseNotNull(overrideConfig.getMaxBackoff(), retryPolicy.getMaxBackoff());
      mergedRetryPolicy.retryAfterFallback =
          chooseNotNull(
              overrideConfig.getRetryAfterFallback(), retryPolicy.getRetryAfterFallback());

      return mergedRetryPolicy;
    }

    void validateSandboxConfig() {
      if (!useSandbox) {
        return;
      }
      if (sandbox == null || sandbox.baseUrl == null || sandbox.baseUrl.isBlank()) {
        throw new AppException(MARKETPLACE_BASE_URL_MISSING, "sandbox.baseUrl");
      }
    }

    private static <T> T chooseNotNull(T value, T defaultValue) {
      return (value != null) ? value : defaultValue;
    }
  }

  @Getter
  @Setter
  public static class Sandbox {

    @NotBlank(message = MARKETPLACE_BASE_URL_MISSING)
    private String baseUrl;

    private String feedbacksBaseUrl;
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

    @NotNull(message = MARKETPLACE_RETRY_POLICY_RETRY_AFTER_FALLBACK_REQUIRED)
    private Duration retryAfterFallback;
  }

  @Getter
  @Setter
  public static class RetryPolicyOverride {

    private Integer maxAttempts;
    private Duration baseBackoff;
    private Duration maxBackoff;
    private Duration retryAfterFallback;
  }
}
