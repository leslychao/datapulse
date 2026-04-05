package io.datapulse.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("datapulse.integration.marketplace-http-logging")
public record MarketplaceHttpLoggingProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("8192") int maxBodyChars) {}
