package io.datapulse.etl.adapter.ozon.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceService(
    String name,
    BigDecimal price
) {}
