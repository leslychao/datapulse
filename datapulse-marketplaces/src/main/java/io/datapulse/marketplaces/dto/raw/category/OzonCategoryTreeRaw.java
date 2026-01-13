package io.datapulse.marketplaces.dto.raw.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OzonCategoryTreeRaw {

  @SerializedName("description_category_id")
  @JsonProperty("description_category_id")
  private Long descriptionCategoryId;

  @SerializedName("category_name")
  @JsonProperty("category_name")
  private String categoryName;

  @SerializedName("disabled")
  @JsonProperty("disabled")
  private Boolean disabled;

  @SerializedName("type_id")
  @JsonProperty("type_id")
  private Long typeId;

  @SerializedName("type_name")
  @JsonProperty("type_name")
  private String typeName;

  @SerializedName("children")
  @JsonProperty("children")
  private List<OzonCategoryTreeRaw> children;
}
