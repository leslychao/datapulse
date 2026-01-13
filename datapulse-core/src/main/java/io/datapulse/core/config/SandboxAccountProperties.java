package io.datapulse.core.config;

import io.datapulse.core.validation.account.ValidMarketplaceConnections;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.ValidationKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Configuration
@Validated
@ValidMarketplaceConnections
@ConfigurationProperties(prefix = "sandbox-account")
public class SandboxAccountProperties {

  private boolean enabled;

  @NotBlank(message = ValidationKeys.ACCOUNT_NAME_REQUIRED)
  private String name;

  @Valid
  private Map<MarketplaceType, ConnectionProperties> connections =
      new EnumMap<>(MarketplaceType.class);

  @Getter
  @Setter
  public static class ConnectionProperties {

    private boolean active;

    private String token;
    private String clientId;
    private String apiKey;
  }
}
