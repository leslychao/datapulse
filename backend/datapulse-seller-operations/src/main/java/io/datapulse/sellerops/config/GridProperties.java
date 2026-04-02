package io.datapulse.sellerops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "datapulse.grid")
public class GridProperties {

    private int defaultPageSize = 50;
    private int maxPageSize = 200;
    private int exportMaxRows = 100_000;
    private int exportTimeoutSeconds = 60;
    private int freshnessThresholdHours = 4;
}
