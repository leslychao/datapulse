package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFbsPosting(
        @JsonProperty("posting_number") String postingNumber,
        @JsonProperty("order_id") long orderId,
        @JsonProperty("order_number") String orderNumber,
        String status,
        @JsonProperty("in_process_at") String inProcessAt,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("shipment_date") String shipmentDate,
        List<OzonFboPosting.OzonPostingProduct> products,
        @JsonProperty("analytics_data") OzonFboPosting.OzonAnalyticsData analyticsData,
        @JsonProperty("financial_data") OzonFboPosting.OzonFinancialData financialData,
        @JsonProperty("delivery_method") OzonDeliveryMethod deliveryMethod,
        OzonCancellation cancellation
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonDeliveryMethod(
            long id,
            String name,
            @JsonProperty("warehouse_id") long warehouseId,
            @JsonProperty("warehouse") String warehouse,
            @JsonProperty("tpl_provider_id") long tplProviderId,
            @JsonProperty("tpl_provider") String tplProvider
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonCancellation(
            @JsonProperty("cancel_reason_id") long cancelReasonId,
            @JsonProperty("cancellation_type") String cancellationType,
            @JsonProperty("cancelled_after_ship") boolean cancelledAfterShip,
            @JsonProperty("affect_cancellation_rating") boolean affectCancellationRating,
            @JsonProperty("cancellation_initiator") String cancellationInitiator
    ) {}
}
