package io.datapulse.etl.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbCatalogCard(
        long nmID,
        String vendorCode,
        String brand,
        String title,
        String subjectName,
        int subjectID,
        long imtID,
        String nmUUID,
        String description,
        List<WbCardSize> sizes,
        String createdAt,
        String updatedAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WbCardSize(
            long chrtID,
            String techSize,
            String wbSize,
            List<String> skus
    ) {}
}
