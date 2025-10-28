package io.datapulse.marketplaces.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbSaleRaw(
    OffsetDateTime date,
    OffsetDateTime lastChangeDate,
    String warehouseName,
    String warehouseType,
    String countryName,
    String oblastOkrugName,
    String regionName,
    String supplierArticle,
    Long nmId,
    String barcode,
    String category,
    String subject,
    String brand,
    String techSize,
    Long incomeID,
    Boolean isSupply,
    Boolean isRealization,
    BigDecimal totalPrice,
    Integer discountPercent,
    BigDecimal spp,
    BigDecimal paymentSaleAmount,
    BigDecimal forPay,
    BigDecimal finishedPrice,
    BigDecimal priceWithDisc,
    String saleID,
    String sticker,
    String gNumber,
    String srid
) {}
