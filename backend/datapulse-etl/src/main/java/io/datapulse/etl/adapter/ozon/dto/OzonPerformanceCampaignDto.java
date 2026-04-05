package io.datapulse.etl.adapter.ozon.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPerformanceCampaignDto(
    long id,
    String title,
    String state,
    @JsonProperty("dailyBudget") BigDecimal dailyBudget,
    @JsonProperty("createdAt") String createdAt,
    @JsonProperty("endedAt") String endedAt,
    @JsonProperty("advObjectType") String advObjectType
) {}
