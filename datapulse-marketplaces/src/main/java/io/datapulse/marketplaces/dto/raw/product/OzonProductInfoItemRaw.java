package io.datapulse.marketplaces.dto.raw.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OzonProductInfoItemRaw {

  /**
   * В ответе /v3/product/info/list поле "id" = product_id продавца.
   */
  @SerializedName("id")
  @JsonProperty("id")
  private Long productId;

  @SerializedName("description_category_id")
  @JsonProperty("description_category_id")
  private Long descriptionCategoryId;

  @SerializedName("offer_id")
  @JsonProperty("offer_id")
  private String offerId;

  @SerializedName("sku")
  @JsonProperty("sku")
  private Long sku;

  @SerializedName("name")
  @JsonProperty("name")
  private String name;

  @SerializedName("is_archived")
  @JsonProperty("is_archived")
  private Boolean archived;
}
