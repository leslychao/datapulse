package io.datapulse.marketplaces.dto.raw.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPostingFboRaw(

    @SerializedName("order_id")
    @JsonProperty("order_id")
    Long orderId,

    @SerializedName("order_number")
    @JsonProperty("order_number")
    String orderNumber,

    @SerializedName("posting_number")
    @JsonProperty("posting_number")
    String postingNumber,

    @SerializedName("status")
    @JsonProperty("status")
    String status,

    @SerializedName("substatus")
    @JsonProperty("substatus")
    String substatus,

    @SerializedName("cancel_reason_id")
    @JsonProperty("cancel_reason_id")
    Long cancelReasonId,

    @SerializedName("created_at")
    @JsonProperty("created_at")
    String createdAt,

    @SerializedName("in_process_at")
    @JsonProperty("in_process_at")
    String inProcessAt,

    @SerializedName("products")
    @JsonProperty("products")
    List<ProductRaw> products,

    @SerializedName("analytics_data")
    @JsonProperty("analytics_data")
    AnalyticsDataRaw analyticsData,

    @SerializedName("financial_data")
    @JsonProperty("financial_data")
    FinancialDataRaw financialData

) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProductRaw(

      @SerializedName("sku")
      @JsonProperty("sku")
      Long sku,

      @SerializedName("name")
      @JsonProperty("name")
      String name,

      @SerializedName("quantity")
      @JsonProperty("quantity")
      Integer quantity,

      @SerializedName("offer_id")
      @JsonProperty("offer_id")
      String offerId,

      @SerializedName("price")
      @JsonProperty("price")
      String price,

      @SerializedName("currency_code")
      @JsonProperty("currency_code")
      String currencyCode,

      @SerializedName("is_marketplace_buyout")
      @JsonProperty("is_marketplace_buyout")
      Boolean isMarketplaceBuyout

  ) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AnalyticsDataRaw(

      @SerializedName("warehouse_id")
      @JsonProperty("warehouse_id")
      Long warehouseId,

      @SerializedName("warehouse_name")
      @JsonProperty("warehouse_name")
      String warehouseName,

      @SerializedName("delivery_type")
      @JsonProperty("delivery_type")
      String deliveryType,

      @SerializedName("city")
      @JsonProperty("city")
      String city,

      @SerializedName("client_delivery_date_begin")
      @JsonProperty("client_delivery_date_begin")
      String clientDeliveryDateBegin,

      @SerializedName("client_delivery_date_end")
      @JsonProperty("client_delivery_date_end")
      String clientDeliveryDateEnd

  ) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FinancialDataRaw(

      @SerializedName("products")
      @JsonProperty("products")
      List<FinancialProductRaw> products

  ) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FinancialProductRaw(

      @SerializedName("product_id")
      @JsonProperty("product_id")
      Long productId,

      @SerializedName("commission_amount")
      @JsonProperty("commission_amount")
      Double commissionAmount,

      @SerializedName("commission_percent")
      @JsonProperty("commission_percent")
      Double commissionPercent,

      @SerializedName("payout")
      @JsonProperty("payout")
      Double payout,

      @SerializedName("old_price")
      @JsonProperty("old_price")
      Double oldPrice,

      @SerializedName("price")
      @JsonProperty("price")
      Double price,

      @SerializedName("total_discount_value")
      @JsonProperty("total_discount_value")
      Double totalDiscountValue,

      @SerializedName("total_discount_percent")
      @JsonProperty("total_discount_percent")
      Double totalDiscountPercent,

      @SerializedName("currency_code")
      @JsonProperty("currency_code")
      String currencyCode,

      @SerializedName("quantity")
      @JsonProperty("quantity")
      Integer quantity

  ) {

  }
}
