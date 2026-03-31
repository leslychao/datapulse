package io.datapulse.etl.adapter.wb.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbSaleItem(
        String saleID,
        String srid,
        String gNumber,
        long nmId,
        String supplierArticle,
        String barcode,
        BigDecimal totalPrice,
        int discountPercent,
        BigDecimal priceWithDisc,
        BigDecimal spp,
        BigDecimal forPay,
        BigDecimal finishedPrice,
        String date,
        String lastChangeDate,
        String regionName,
        String warehouseName
) {}
