package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexReturnItem(
    Long marketSku,
    String shopSku,
    int count,
    String decisionType,
    YandexReturnReason returnReason
) {}
