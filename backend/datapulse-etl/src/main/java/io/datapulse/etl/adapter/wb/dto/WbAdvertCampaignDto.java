package io.datapulse.etl.adapter.wb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbAdvertCampaignDto(
    long advertId,
    int type,
    int status,
    long dailyBudget,
    String createTime,
    String changeTime,
    String startTime,
    String endTime,
    String name
) {}
