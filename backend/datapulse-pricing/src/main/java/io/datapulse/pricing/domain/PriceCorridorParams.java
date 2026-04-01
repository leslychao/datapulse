package io.datapulse.pricing.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceCorridorParams(
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
