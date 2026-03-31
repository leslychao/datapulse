package io.datapulse.etl.adapter.wb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbStockItem(
        long nmId,
        long chrtId,
        int warehouseId,
        String warehouseName,
        String regionName,
        int quantity,
        int inWayToClient,
        int inWayFromClient
) {}
