package io.datapulse.etl.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbCatalogResponse(
        List<WbCatalogCard> cards,
        WbCatalogCursor cursor
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WbCatalogCursor(
            String updatedAt,
            long nmID,
            int total
    ) {}
}
