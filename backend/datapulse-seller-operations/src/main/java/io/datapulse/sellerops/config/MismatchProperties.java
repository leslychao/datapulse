package io.datapulse.sellerops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@Validated
@ConfigurationProperties(prefix = "datapulse.mismatch")
public class MismatchProperties {

    private BigDecimal priceWarningThresholdPct = new BigDecimal("1");
    private BigDecimal priceCriticalThresholdPct = new BigDecimal("5");
    private int stockDeltaUnitsThreshold = 10;
    private BigDecimal stockDeltaPctThreshold = new BigDecimal("20");
    private int financeGapHoursThreshold = 48;
}
