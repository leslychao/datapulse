package io.datapulse.marketplaces.dto.raw.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonReturnRaw(

    @SerializedName("id")
    @JsonProperty("id")
    String id,

    @SerializedName("company_id")
    @JsonProperty("company_id")
    String companyId,

    @SerializedName("schema")
    @JsonProperty("schema")
    String schema, // Fbo / Fbs

    @SerializedName("order_id")
    @JsonProperty("order_id")
    String orderId,

    @SerializedName("order_number")
    @JsonProperty("order_number")
    String orderNumber,

    @SerializedName("posting_number")
    @JsonProperty("posting_number")
    String postingNumber,

    @SerializedName("return_reason_name")
    @JsonProperty("return_reason_name")
    String returnReasonName,

    @SerializedName("product")
    @JsonProperty("product")
    ProductRaw product,

    @SerializedName("logistic")
    @JsonProperty("logistic")
    LogisticRaw logistic,

    @SerializedName("place")
    @JsonProperty("place")
    PlaceRaw place,

    @SerializedName("target_place")
    @JsonProperty("target_place")
    PlaceRaw targetPlace

) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProductRaw(

      @SerializedName("sku")
      @JsonProperty("sku")
      String sku,

      @SerializedName("offer_id")
      @JsonProperty("offer_id")
      String offerId,

      @SerializedName("name")
      @JsonProperty("name")
      String name,

      @SerializedName("quantity")
      @JsonProperty("quantity")
      Integer quantity

  ) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LogisticRaw(

      @SerializedName("return_date")
      @JsonProperty("return_date")
      String returnDate,

      @SerializedName("barcode")
      @JsonProperty("barcode")
      String barcode

  ) {

  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PlaceRaw(

      @SerializedName("id")
      @JsonProperty("id")
      String id,

      @SerializedName("name")
      @JsonProperty("name")
      String name

  ) {

  }
}
