package io.datapulse.marketplaces.dto.raw.sales;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public record WbSupplierSaleRaw(
    @SerializedName("date")
    @JsonProperty("date")
    String date,

    @SerializedName("lastChangeDate")
    @JsonProperty("lastChangeDate")
    String lastChangeDate,

    @SerializedName("warehouseName")
    @JsonProperty("warehouseName")
    String warehouseName,

    @SerializedName("warehouseType")
    @JsonProperty("warehouseType")
    String warehouseType,

    @SerializedName("countryName")
    @JsonProperty("countryName")
    String countryName,

    @SerializedName("oblastOkrugName")
    @JsonProperty("oblastOkrugName")
    String oblastOkrugName,

    @SerializedName("regionName")
    @JsonProperty("regionName")
    String regionName,

    @SerializedName("supplierArticle")
    @JsonProperty("supplierArticle")
    String supplierArticle,

    @SerializedName("nmId")
    @JsonProperty("nmId")
    Long nmId,

    @SerializedName("barcode")
    @JsonProperty("barcode")
    String barcode,

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

    @SerializedName("incomeID")
    @JsonProperty("incomeID")
    Long incomeId,

    @SerializedName("isSupply")
    @JsonProperty("isSupply")
    Boolean isSupply,

    @SerializedName("isRealization")
    @JsonProperty("isRealization")
    Boolean isRealization,

    @SerializedName("totalPrice")
    @JsonProperty("totalPrice")
    BigDecimal totalPrice,

    @SerializedName("discountPercent")
    @JsonProperty("discountPercent")
    Integer discountPercent,

    @SerializedName("spp")
    @JsonProperty("spp")
    Integer spp,

    @SerializedName("paymentSaleAmount")
    @JsonProperty("paymentSaleAmount")
    BigDecimal paymentSaleAmount,

    @SerializedName("forPay")
    @JsonProperty("forPay")
    BigDecimal forPay,

    @SerializedName("finishedPrice")
    @JsonProperty("finishedPrice")
    BigDecimal finishedPrice,

    @SerializedName("priceWithDisc")
    @JsonProperty("priceWithDisc")
    BigDecimal priceWithDisc,

    @SerializedName("saleID")
    @JsonProperty("saleID")
    String saleId,

    @SerializedName("sticker")
    @JsonProperty("sticker")
    String sticker,

    @SerializedName("gNumber")
    @JsonProperty("gNumber")
    String gNumber,

    @SerializedName("srid")
    @JsonProperty("srid")
    String srid
) {}
