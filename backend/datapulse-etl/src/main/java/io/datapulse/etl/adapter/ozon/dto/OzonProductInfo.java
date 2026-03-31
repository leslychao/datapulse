package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonProductInfo(
        long id,
        @JsonProperty("offer_id") String offerId,
        String name,
        @JsonProperty("barcode") String barcode,
        List<String> barcodes,
        @JsonProperty("description_category_id") long descriptionCategoryId,
        @JsonProperty("type_id") long typeId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("is_archived") boolean isArchived,
        @JsonProperty("is_autoarchived") boolean isAutoarchived,
        OzonProductVisibility visibility,
        List<OzonProductSource> sources,
        List<OzonProductStock> stocks
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonProductVisibility(
            @JsonProperty("visible") boolean visible
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonProductSource(
            String source,
            String sku
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonProductStock(
            String type,
            int present,
            int reserved
    ) {}
}
