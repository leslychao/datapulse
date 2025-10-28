package io.datapulse.marketplaces.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbStockRaw(
    OffsetDateTime lastChangeDate,
    String warehouseName,
    String supplierArticle,
    Long nmId,
    String barcode,
    Integer quantity,
    Integer inWayToClient,
    Integer inWayFromClient,
    Integer quantityFull,
    String category,
    String subject,
    String brand,
    String techSize,
    @JsonProperty("Price") BigDecimal price,
    @JsonProperty("Discount") Integer discount,
    Boolean isSupply,
    Boolean isRealization,
    @JsonProperty("SCCode") String scCode
) {}
