package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPriceItem(
        @JsonProperty("product_id") long productId,
        @JsonProperty("offer_id") String offerId,
        OzonPriceObject price,
        @JsonProperty("price_indexes") OzonPriceIndexes priceIndexes,
        OzonCommissions commissions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonPriceObject(
            String price,
            @JsonProperty("old_price") String oldPrice,
            @JsonProperty("min_price") String minPrice,
            @JsonProperty("marketing_price") String marketingPrice,
            @JsonProperty("marketing_seller_price") String marketingSellerPrice,
            @JsonProperty("currency_code") String currencyCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonPriceIndexes(
            @JsonProperty("price_index") String priceIndex
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonCommissions(
            @JsonProperty("fbo_commission_percent") double fboCommissionPercent,
            @JsonProperty("fbs_commission_percent") double fbsCommissionPercent,
            @JsonProperty("fbo_commission_value") double fboCommissionValue,
            @JsonProperty("fbs_commission_value") double fbsCommissionValue
    ) {}
}
