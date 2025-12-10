package io.datapulse.marketplaces.dto.raw.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OzonCategoryTreeRaw {

  @SerializedName("description_category_id")
  private Long descriptionCategoryId;

  @SerializedName("category_name")
  private String categoryName;

  @SerializedName("disabled")
  private Boolean disabled;

  @SerializedName("type_id")
  private Long typeId;

  @SerializedName("type_name")
  private String typeName;

  @SerializedName("children")
  private List<OzonCategoryTreeRaw> children;
}
