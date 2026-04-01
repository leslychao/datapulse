package io.datapulse.sellerops.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GridProperties.class)
public class SellerOpsConfig {
}
