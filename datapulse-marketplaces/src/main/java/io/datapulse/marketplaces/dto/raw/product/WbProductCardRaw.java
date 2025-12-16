package io.datapulse.marketplaces.dto.raw.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WbProductCardRaw {

  @SerializedName("nmID")
  @JsonProperty("nmID")
  private Long nmId;

  @SerializedName("vendorCode")
  @JsonProperty("vendorCode")
  private String vendorCode;

  @SerializedName("subjectID")
  @JsonProperty("subjectID")
  private Long subjectId;

  @SerializedName("subjectName")
  @JsonProperty("subjectName")
  private String subjectName;

  @SerializedName("brand")
  @JsonProperty("brand")
  private String brand;

  @SerializedName("title")
  @JsonProperty("title")
  private String title;

  @SerializedName("imtID")
  @JsonProperty("imtID")
  private Long imtId;

  @SerializedName("createdAt")
  @JsonProperty("createdAt")
  private String createdAt;

  @SerializedName("updatedAt")
  @JsonProperty("updatedAt")
  private String updatedAt;
}
