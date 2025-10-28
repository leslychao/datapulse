package io.datapulse.marketplaces.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbSaleRaw(
    String supplierArticle,
    Long   nmId,
    String barcode,
    Integer quantity,
    BigDecimal priceWithDiscRub,
    BigDecimal forPay,
    Boolean isCancel,
    Boolean isReturn,
    OffsetDateTime dateCreated
) {}
