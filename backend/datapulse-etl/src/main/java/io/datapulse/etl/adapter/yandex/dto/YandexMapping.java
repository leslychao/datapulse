package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexMapping(
    Long marketSku,
    String marketSkuName,
    String marketModelName,
    Integer marketCategoryId,
    String marketCategoryName
) {}
