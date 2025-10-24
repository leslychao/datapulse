package io.datapulse.core.config;

import java.time.Duration;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "marketplace")
public class MarketplaceProperties {

  private Map<String, Provider> providers;

  @Getter
  @Setter
  public static class Provider {

    private String baseUrl;

    private Endpoints endpoints;

    private Resilience resilience;
  }

  @Getter
  @Setter
  public static class Endpoints {

    private String sales;
    private String stock;
    private String finance;
    private String reviews;
  }

  @Getter
  @Setter
  public static class Resilience {

    private Integer limitForPeriod;

    private Integer maxConcurrentCalls;

    private Integer maxAttempts;

    private Duration baseBackoff;

    private Duration maxJitter;

    private Duration retryAfterFallback;

    private Duration limitRefreshPeriod;

    private Duration tokenWaitTimeout;

    private Duration bulkheadWait;
  }
}
