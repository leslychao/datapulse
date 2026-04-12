package io.datapulse.etl.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexPromo(
    String promoId,
    String name,
    String status,
    List<String> channels,
    String startDate,
    String endDate,
    String mechanicsType,
    String participationType
) {}
