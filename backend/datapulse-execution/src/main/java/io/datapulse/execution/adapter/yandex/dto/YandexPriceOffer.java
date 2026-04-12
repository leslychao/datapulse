package io.datapulse.execution.adapter.yandex.dto;

public record YandexPriceOffer(
    String offerId,
    YandexPriceValue price
) {
}
