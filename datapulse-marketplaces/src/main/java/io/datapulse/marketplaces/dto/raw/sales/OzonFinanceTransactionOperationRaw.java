package io.datapulse.marketplaces.dto.raw.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceTransactionOperationRaw(

    @SerializedName("operation_id")
    @JsonProperty("operation_id")
    Long operationId,

    @SerializedName("operation_type")
    @JsonProperty("operation_type")
    String operationType,

    @SerializedName("operation_date")
    @JsonProperty("operation_date")
    String operationDate,

    @SerializedName("operation_type_name")
    @JsonProperty("operation_type_name")
    String operationTypeName,

    @SerializedName("amount")
    @JsonProperty("amount")
    Double amount,

    @SerializedName("type")
    @JsonProperty("type")
    String type,

    @SerializedName("accruals_for_sale")
    @JsonProperty("accruals_for_sale")
    Double accrualsForSale,

    @SerializedName("sale_commission")
    @JsonProperty("sale_commission")
    Double saleCommission,

    @SerializedName("delivery_charge")
    @JsonProperty("delivery_charge")
    Double deliveryCharge,

    @SerializedName("return_delivery_charge")
    @JsonProperty("return_delivery_charge")
    Double returnDeliveryCharge,

    @SerializedName("posting")
    @JsonProperty("posting")
    PostingRaw posting

) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PostingRaw(

      @SerializedName("posting_number")
      @JsonProperty("posting_number")
      String postingNumber,

      @SerializedName("delivery_schema")
      @JsonProperty("delivery_schema")
      String deliverySchema,

      @SerializedName("order_date")
      @JsonProperty("order_date")
      String orderDate,

      @SerializedName("warehouse_id")
      @JsonProperty("warehouse_id")
      Long warehouseId

  ) {

  }
}
