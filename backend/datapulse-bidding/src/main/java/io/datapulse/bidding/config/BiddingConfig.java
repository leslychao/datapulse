package io.datapulse.bidding.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BiddingProperties.class)
public class BiddingConfig {
}
