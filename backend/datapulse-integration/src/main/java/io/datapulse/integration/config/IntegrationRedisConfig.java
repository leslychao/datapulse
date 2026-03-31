package io.datapulse.integration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties({IntegrationProperties.class, RateLimitProperties.class})
public class IntegrationRedisConfig {

    @Bean("rateLimitScheduler")
    public ScheduledExecutorService rateLimitScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("rate-limit-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }
}
