package io.datapulse.etl.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbPriceGood(
        long nmID,
        String vendorCode,
        List<WbPriceSize> sizes,
        String currencyIsoCode4217,
        int discount,
        int clubDiscount,
        boolean editableSizePrice
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WbPriceSize(
            long sizeID,
            long price,
            long discountedPrice,
            long clubDiscountedPrice,
            String techSizeName
    ) {}
}
