package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonReturnItem(
        long id,
        @JsonProperty("return_id") long returnId,
        @JsonProperty("order_id") long orderId,
        @JsonProperty("order_number") String orderNumber,
        @JsonProperty("posting_number") String postingNumber,
        String status,
        @JsonProperty("return_reason_name") String returnReasonName,
        @JsonProperty("return_date") String returnDate,
        @JsonProperty("accepted_from_customer_moment") String acceptedFromCustomerMoment,
        OzonReturnProduct product,
        @JsonProperty("is_opened") boolean isOpened,
        @JsonProperty("place_id") long placeId
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonReturnProduct(
            @JsonProperty("offer_id") String offerId,
            String name,
            long sku,
            int quantity,
            OzonReturnPrice price
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonReturnPrice(
            String price,
            @JsonProperty("currency_code") String currencyCode
    ) {}
}
