package io.datapulse.core.config;

import io.datapulse.domain.MarketplaceType;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "system-account")
public class SystemAccountProperties {

  private boolean enabled;
  private String name;
  private Map<MarketplaceType, ConnectionProperties> connections = new EnumMap<>(
      MarketplaceType.class);

  public void setConnections(Map<MarketplaceType, ConnectionProperties> connections) {
    this.connections =
        connections != null
            ? connections
            : new EnumMap<>(MarketplaceType.class);
  }

  @Getter
  @Setter
  public static class ConnectionProperties {

    private boolean active;
    private String token;
    private String clientId;
    private String apiKey;
  }
}
