package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonReturnResponse(
        List<OzonReturnItem> returns,
        @JsonProperty("last_id") long lastId,
        @JsonProperty("has_next") boolean hasNext
) {}
