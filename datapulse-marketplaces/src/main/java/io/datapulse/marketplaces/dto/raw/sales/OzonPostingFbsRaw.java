package io.datapulse.marketplaces.dto.raw.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPostingFbsRaw(

    @SerializedName("posting_number")
    @JsonProperty("posting_number")
    String postingNumber,

    @SerializedName("order_id")
    @JsonProperty("order_id")
    Long orderId,

    @SerializedName("order_number")
    @JsonProperty("order_number")
    String orderNumber,

    @SerializedName("status")
    @JsonProperty("status")
    String status,

    @SerializedName("substatus")
    @JsonProperty("substatus")
    String substatus,

    @SerializedName("in_process_at")
    @JsonProperty("in_process_at")
    String inProcessAt,

    @SerializedName("shipment_date")
    @JsonProperty("shipment_date")
    String shipmentDate,

    @SerializedName("shipment_date_without_delay")
    @JsonProperty("shipment_date_without_delay")
    String shipmentDateWithoutDelay,

    @SerializedName("delivery_method")
    @JsonProperty("delivery_method")
    DeliveryMethodRaw deliveryMethod,

    @SerializedName("products")
    @JsonProperty("products")
    List<ProductRaw> products

) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DeliveryMethodRaw(

      @SerializedName("id")
      @JsonProperty("id")
      Long id,

      @SerializedName("name")
      @JsonProperty("name")
      String name,

      @SerializedName("warehouse_id")
      @JsonProperty("warehouse_id")
      Long warehouseId,

      @SerializedName("warehouse")
      @JsonProperty("warehouse")
      String warehouse,

      @SerializedName("tpl_provider_id")
      @JsonProperty("tpl_provider_id")
      Long tplProviderId,

      @SerializedName("tpl_provider")
      @JsonProperty("tpl_provider")
      String tplProvider

  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProductRaw(

      @SerializedName("offer_id")
      @JsonProperty("offer_id")
      String offerId,

      @SerializedName("sku")
      @JsonProperty("sku")
      Long sku,

      @SerializedName("name")
      @JsonProperty("name")
      String name,

      @SerializedName("quantity")
      @JsonProperty("quantity")
      Integer quantity,

      @SerializedName("price")
      @JsonProperty("price")
      String price,

      @SerializedName("currency_code")
      @JsonProperty("currency_code")
      String currencyCode

  ) {}
}
