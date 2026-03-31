package io.datapulse.etl.adapter.wb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbReturnItem(
        long nmId,
        String barcode,
        String srid,
        long orderId,
        long shkId,
        String stickerId,
        String brand,
        String subjectName,
        String techSize,
        String status,
        int isStatusActive,
        String returnType,
        String reason,
        String orderDt,
        String completedDt,
        String readyToReturnDt,
        String expiredDt,
        long dstOfficeId,
        String dstOfficeAddress
) {}
