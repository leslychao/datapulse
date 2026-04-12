package io.datapulse.etl.adapter.yandex.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexGps(
    BigDecimal latitude,
    BigDecimal longitude
) {}
