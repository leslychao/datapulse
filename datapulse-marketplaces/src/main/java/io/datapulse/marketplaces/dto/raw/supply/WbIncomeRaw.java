package io.datapulse.marketplaces.dto.raw.supply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbIncomeRaw(

    @SerializedName("incomeId")
    @JsonProperty("incomeId")
    Long incomeId,

    @SerializedName("number")
    @JsonProperty("number")
    String number,

    @SerializedName("date")
    @JsonProperty("date")
    String date,

    @SerializedName("lastChangeDate")
    @JsonProperty("lastChangeDate")
    String lastChangeDate,

    @SerializedName("supplierArticle")
    @JsonProperty("supplierArticle")
    String supplierArticle,

    @SerializedName("techSize")
    @JsonProperty("techSize")
    String techSize,

    @SerializedName("barcode")
    @JsonProperty("barcode")
    String barcode,

    @SerializedName("quantity")
    @JsonProperty("quantity")
    Integer quantity,

    @SerializedName("totalPrice")
    @JsonProperty("totalPrice")
    Integer totalPrice,

    @SerializedName("dateClose")
    @JsonProperty("dateClose")
    String dateClose,

    @SerializedName("warehouseName")
    @JsonProperty("warehouseName")
    String warehouseName,

    @SerializedName("nmId")
    @JsonProperty("nmId")
    Long nmId,

    @SerializedName("status")
    @JsonProperty("status")
    String status

) {

}
