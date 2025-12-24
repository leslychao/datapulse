package io.datapulse.marketplaces.dto.raw.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbStockRaw(

    @SerializedName("lastChangeDate")
    @JsonProperty("lastChangeDate")
    String lastChangeDate,

    @SerializedName("warehouseName")
    @JsonProperty("warehouseName")
    String warehouseName,

    @SerializedName("supplierArticle")
    @JsonProperty("supplierArticle")
    String supplierArticle,

    @SerializedName("nmId")
    @JsonProperty("nmId")
    Long nmId,

    @SerializedName("barcode")
    @JsonProperty("barcode")
    String barcode,

    @SerializedName("quantity")
    @JsonProperty("quantity")
    Integer quantity,

    @SerializedName("inWayToClient")
    @JsonProperty("inWayToClient")
    Integer inWayToClient,

    @SerializedName("inWayFromClient")
    @JsonProperty("inWayFromClient")
    Integer inWayFromClient,

    @SerializedName("quantityFull")
    @JsonProperty("quantityFull")
    Integer quantityFull,

    @SerializedName("category")
    @JsonProperty("category")
    String category,

    @SerializedName("subject")
    @JsonProperty("subject")
    String subject,

    @SerializedName("brand")
    @JsonProperty("brand")
    String brand,

    @SerializedName("techSize")
    @JsonProperty("techSize")
    String techSize,

    @SerializedName("Price")
    @JsonProperty("Price")
    Integer price,

    @SerializedName("Discount")
    @JsonProperty("Discount")
    Integer discount,

    @SerializedName("isSupply")
    @JsonProperty("isSupply")
    Boolean isSupply,

    @SerializedName("isRealization")
    @JsonProperty("isRealization")
    Boolean isRealization,

    @SerializedName("SCCode")
    @JsonProperty("SCCode")
    String scCode

) {

}
