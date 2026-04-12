package io.datapulse.etl.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexOrderItem(
    String offerId,
    String offerName,
    Long marketSku,
    int count,
    List<YandexOrderPrice> prices
) {}
