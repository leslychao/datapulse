package io.datapulse.integration.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@Getter
@RequiredArgsConstructor
@ConfigurationProperties("datapulse.integration")
public class IntegrationProperties {

    private final Wildberries wildberries;
    private final Ozon ozon;
    private final HealthCheck healthCheck;

    @Validated
    @Getter
    @RequiredArgsConstructor
    public static class Wildberries {

        @NotBlank
        private final String contentBaseUrl;

        @NotBlank
        private final String pricesBaseUrl;

        private final String pricesWriteBaseUrl;

        @NotBlank
        private final String statisticsBaseUrl;

        @NotBlank
        private final String analyticsBaseUrl;

        @NotBlank
        private final String marketplaceBaseUrl;

        @NotBlank
        private final String advertBaseUrl;

        @NotBlank
        private final String promoBaseUrl;

        private final String sandboxApiToken;

        public String getPricesWriteBaseUrl() {
            return pricesWriteBaseUrl != null ? pricesWriteBaseUrl : pricesBaseUrl;
        }
    }

    @Validated
    @Getter
    @RequiredArgsConstructor
    public static class Ozon {

        @NotBlank
        private final String sellerBaseUrl;

        private final String sellerWriteBaseUrl;

        @NotBlank
        private final String performanceBaseUrl;

        public String getSellerWriteBaseUrl() {
            return sellerWriteBaseUrl != null ? sellerWriteBaseUrl : sellerBaseUrl;
        }
    }

    @Validated
    @Getter
    @RequiredArgsConstructor
    public static class HealthCheck {

        private final Duration interval;

        @Positive
        private final int failureThreshold;

        public Duration getInterval() {
            return interval != null ? interval : Duration.ofMinutes(15);
        }

        public int getFailureThreshold() {
            return failureThreshold > 0 ? failureThreshold : 3;
        }
    }
}
