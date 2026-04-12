package io.datapulse.etl.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexDelivery(
    String deliveryPartnerType,
    String type,
    List<YandexShipment> shipments,
    YandexRegion region
) {}
