package io.datapulse.execution.adapter.yandex.dto;

import java.math.BigDecimal;

public record YandexPriceValue(
    BigDecimal value,
    String currencyId
) {
}
