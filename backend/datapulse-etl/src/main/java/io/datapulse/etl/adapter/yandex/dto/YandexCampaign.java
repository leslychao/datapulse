package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexCampaign(
    long id,
    YandexBusiness business,
    String placementType,
    String apiAvailability,
    String domain
) {}
