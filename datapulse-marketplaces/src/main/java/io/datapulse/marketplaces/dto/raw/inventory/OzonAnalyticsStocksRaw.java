package io.datapulse.marketplaces.dto.raw.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonAnalyticsStocksRaw(

    @SerializedName("sku")
    @JsonProperty("sku")
    Long sku,

    @SerializedName("offer_id")
    @JsonProperty("offer_id")
    String offerId,

    @SerializedName("name")
    @JsonProperty("name")
    String name,

    @SerializedName("cluster_id")
    @JsonProperty("cluster_id")
    Long clusterId,

    @SerializedName("cluster_name")
    @JsonProperty("cluster_name")
    String clusterName,

    @SerializedName("warehouse_id")
    @JsonProperty("warehouse_id")
    Long warehouseId,

    @SerializedName("warehouse_name")
    @JsonProperty("warehouse_name")
    String warehouseName,

    @SerializedName("available_stock_count")
    @JsonProperty("available_stock_count")
    Integer availableStockCount,

    @SerializedName("valid_stock_count")
    @JsonProperty("valid_stock_count")
    Integer validStockCount,

    @SerializedName("transit_stock_count")
    @JsonProperty("transit_stock_count")
    Integer transitStockCount,

    @SerializedName("requested_stock_count")
    @JsonProperty("requested_stock_count")
    Integer requestedStockCount,

    @SerializedName("return_from_customer_stock_count")
    @JsonProperty("return_from_customer_stock_count")
    Integer returnFromCustomerStockCount,

    @SerializedName("return_to_seller_stock_count")
    @JsonProperty("return_to_seller_stock_count")
    Integer returnToSellerStockCount,

    @SerializedName("idc")
    @JsonProperty("idc")
    Double idc,

    @SerializedName("ads")
    @JsonProperty("ads")
    Double ads,

    @SerializedName("turnover_grade")
    @JsonProperty("turnover_grade")
    String turnoverGrade
) {

}
