package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFboPosting(
        @JsonProperty("posting_number") String postingNumber,
        @JsonProperty("order_id") long orderId,
        @JsonProperty("order_number") String orderNumber,
        String status,
        @JsonProperty("in_process_at") String inProcessAt,
        @JsonProperty("created_at") String createdAt,
        List<OzonPostingProduct> products,
        @JsonProperty("analytics_data") OzonAnalyticsData analyticsData,
        @JsonProperty("financial_data") OzonFinancialData financialData
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonPostingProduct(
            @JsonProperty("sku") long sku,
            @JsonProperty("offer_id") String offerId,
            String name,
            int quantity,
            String price,
            @JsonProperty("currency_code") String currencyCode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonAnalyticsData(
            String region,
            String city,
            @JsonProperty("delivery_type") String deliveryType,
            @JsonProperty("warehouse_name") String warehouseName,
            @JsonProperty("warehouse_id") Long warehouseId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonFinancialData(
            @JsonProperty("posting_services") OzonPostingServices postingServices,
            @JsonProperty("cluster_from") String clusterFrom,
            @JsonProperty("cluster_to") String clusterTo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonPostingServices(
            @JsonProperty("marketplace_service_item_fulfillment") double fulfillment,
            @JsonProperty("marketplace_service_item_pickup") double pickup,
            @JsonProperty("marketplace_service_item_dropoff_pvz") double dropoffPvz,
            @JsonProperty("marketplace_service_item_dropoff_sc") double dropoffSc,
            @JsonProperty("marketplace_service_item_dropoff_ff") double dropoffFf,
            @JsonProperty("marketplace_service_item_direct_flow_trans") double directFlowTrans,
            @JsonProperty("marketplace_service_item_return_flow_trans") double returnFlowTrans,
            @JsonProperty("marketplace_service_item_deliv_to_customer") double delivToCustomer,
            @JsonProperty("marketplace_service_item_return_not_deliv_to_customer") double returnNotDelivToCustomer,
            @JsonProperty("marketplace_service_item_return_part_goods_customer") double returnPartGoodsCustomer,
            @JsonProperty("marketplace_service_item_return_after_deliv_to_customer") double returnAfterDelivToCustomer
    ) {}
}
