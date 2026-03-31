package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPostingResponse(
        OzonPostingResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonPostingResult(
            @JsonProperty("has_next") boolean hasNext,
            List<OzonFboPosting> postings
    ) {}
}
