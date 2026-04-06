package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompositeParams(
    List<ComponentConfig> components,
    BigDecimal roundingStep,
    TargetMarginParams.RoundingDirection roundingDirection
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComponentConfig(
        PolicyType strategyType,
        BigDecimal weight,
        String strategyParams
    ) {}
}
