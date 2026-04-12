package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexOfferCampaign(
    long campaignId,
    String status
) {}
