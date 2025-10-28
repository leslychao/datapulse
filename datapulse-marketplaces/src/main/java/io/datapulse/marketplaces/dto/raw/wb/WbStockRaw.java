package io.datapulse.marketplaces.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbStockRaw(
    String supplierArticle,
    Long   nmId,
    String warehouseName,
    Integer quantity,      // доступно
    Integer inWayToClient, // в пути
    Integer inWayFromClient,
    java.time.OffsetDateTime lastChangeDate
) {}
