package io.datapulse.etl.adapter.wb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbPromotionNomenclatureDto(
        @JsonProperty("nmID") Long nmId,
        String vendorCode,
        @JsonProperty("actionPrice") Double actionPrice,
        @JsonProperty("planPrice") Double planPrice,
        Double price,
        @JsonProperty("inAction") Boolean inAction
) {

    public double effectivePromoPrice() {
        if (actionPrice != null && actionPrice > 0) {
            return actionPrice;
        }
        return planPrice != null ? planPrice : 0;
    }
}
