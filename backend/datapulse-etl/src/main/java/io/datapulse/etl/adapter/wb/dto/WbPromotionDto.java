package io.datapulse.etl.adapter.wb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbPromotionDto(
        long id,
        String name,
        @JsonProperty("startDateTime") String startDateTime,
        @JsonProperty("endDateTime") String endDateTime,
        String type,
        String description,
        @JsonProperty("inAction") Boolean inAction,
        @JsonProperty("participationPercentage") Integer participationPercentage
) {}
