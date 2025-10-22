package ru.vkim.datapulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;

@Configuration
public class IntegrationChannelsConfig {

    @Bean public DirectChannel wbSalesInput() { return new DirectChannel(); }
    @Bean public DirectChannel wbStocksInput() { return new DirectChannel(); }
    @Bean public DirectChannel wbFinanceInput() { return new DirectChannel(); }
    @Bean public DirectChannel wbReviewsInput() { return new DirectChannel(); }
    @Bean public DirectChannel wbAdsInput() { return new DirectChannel(); }
}
