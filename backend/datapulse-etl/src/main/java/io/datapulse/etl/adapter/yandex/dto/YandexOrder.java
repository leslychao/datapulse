package io.datapulse.etl.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexOrder(
    long id,
    String status,
    String substatus,
    String creationDate,
    String updatedAt,
    String paymentType,
    String programType,
    long campaignId,
    List<YandexOrderItem> items,
    YandexDelivery delivery
) {}
