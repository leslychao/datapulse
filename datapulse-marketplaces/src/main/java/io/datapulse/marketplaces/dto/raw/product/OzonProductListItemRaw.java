package io.datapulse.marketplaces.dto.raw.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OzonProductListItemRaw {

  @SerializedName("product_id")
  @JsonProperty("product_id")
  private Long productId;

  @SerializedName("offer_id")
  @JsonProperty("offer_id")
  private String offerId;

  @SerializedName("archived")
  @JsonProperty("archived")
  private Boolean archived;

  @SerializedName("has_fbo_stocks")
  @JsonProperty("has_fbo_stocks")
  private Boolean hasFboStocks;

  @SerializedName("has_fbs_stocks")
  @JsonProperty("has_fbs_stocks")
  private Boolean hasFbsStocks;

  @SerializedName("is_discounted")
  @JsonProperty("is_discounted")
  private Boolean discounted;
}
