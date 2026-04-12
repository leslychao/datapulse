package io.datapulse.etl.adapter.yandex.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexWeightDimensions(
    BigDecimal length,
    BigDecimal width,
    BigDecimal height,
    BigDecimal weight
) {}
