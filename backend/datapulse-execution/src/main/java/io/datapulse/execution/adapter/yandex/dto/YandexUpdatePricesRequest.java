package io.datapulse.execution.adapter.yandex.dto;

import java.util.List;

public record YandexUpdatePricesRequest(
    List<YandexPriceOffer> offers
) {
}
