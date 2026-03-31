package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonCategoryTreeResponse(
        List<OzonCategoryNode> result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonCategoryNode(
            @JsonProperty("description_category_id") long descriptionCategoryId,
            @JsonProperty("category_name") String categoryName,
            boolean disabled,
            List<OzonCategoryNode> children
    ) {}
}
