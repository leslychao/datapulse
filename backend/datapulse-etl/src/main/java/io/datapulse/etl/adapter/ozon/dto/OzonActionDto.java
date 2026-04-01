package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonActionDto(
        long id,
        String title,
        @JsonProperty("action_type") String actionType,
        @JsonProperty("date_start") String dateStart,
        @JsonProperty("date_end") String dateEnd,
        String description,
        @JsonProperty("freeze_date") String freezeDate,
        @JsonProperty("is_participating") Boolean isParticipating,
        @JsonProperty("potential_products_count") Integer potentialProductsCount,
        @JsonProperty("participating_products_count") Integer participatingProductsCount,
        @JsonProperty("banned_products_count") Integer bannedProductsCount,
        @JsonProperty("discount_type") String discountType,
        @JsonProperty("discount_value") Double discountValue
) {}
