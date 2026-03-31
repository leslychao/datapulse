package io.datapulse.etl.adapter.wb.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbOrderItem(
        String srid,
        String gNumber,
        long nmId,
        String supplierArticle,
        String barcode,
        BigDecimal totalPrice,
        int discountPercent,
        BigDecimal priceWithDisc,
        BigDecimal spp,
        BigDecimal finishedPrice,
        boolean isCancel,
        String cancelDate,
        boolean isSupply,
        boolean isRealization,
        String date,
        String lastChangeDate,
        String regionName,
        String warehouseName
) {}
