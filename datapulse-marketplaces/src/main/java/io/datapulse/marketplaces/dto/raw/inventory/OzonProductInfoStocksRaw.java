package io.datapulse.marketplaces.dto.raw.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonProductInfoStocksRaw(

    @SerializedName("offer_id")
    @JsonProperty("offer_id")
    String offerId,

    @SerializedName("product_id")
    @JsonProperty("product_id")
    Long productId,

    @SerializedName("stocks")
    @JsonProperty("stocks")
    List<StockRaw> stocks
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StockRaw(

      @SerializedName("present")
      @JsonProperty("present")
      Integer present,

      @SerializedName("reserved")
      @JsonProperty("reserved")
      Integer reserved,

      @SerializedName("shipment_type")
      @JsonProperty("shipment_type")
      String shipmentType,

      @SerializedName("sku")
      @JsonProperty("sku")
      Long sku,

      @SerializedName("type")
      @JsonProperty("type")
      String type,

      @SerializedName("warehouse_ids")
      @JsonProperty("warehouse_ids")
      List<Long> warehouseIds
  ) {

  }
}
