package io.datapulse.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Data
@Validated
@ConfigurationProperties("datapulse.integration.rate-limits")
public class RateLimitProperties {

    private double increasePct = 0.2;

    private double decreaseFactor = 0.5;

    private int stabilityWindow = 20;

    private double minRate = 1.0 / 60.0;

    private Map<String, GroupOverride> groups = new HashMap<>();

    @Data
    public static class GroupOverride {
        private Double initialRate;
        private Integer burst;
        private Double minRate;
        private Double maxRate;
    }
}
