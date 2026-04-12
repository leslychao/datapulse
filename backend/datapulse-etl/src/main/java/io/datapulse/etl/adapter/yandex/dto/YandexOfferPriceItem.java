package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexOfferPriceItem(
    String offerId,
    YandexBasicPrice price,
    String updatedAt
) {}
