package io.datapulse.marketplaces.dto.raw.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbFinanceRaw(
    String operationId,
    String rrn,                 // условный «связующий» идентификатор, если есть
    String operationType,       // SALE/RETURN/...
    OffsetDateTime operationDate,
    BigDecimal amount,
    BigDecimal commission,
    BigDecimal delivery,
    BigDecimal storageFee,
    BigDecimal penalty,
    BigDecimal marketing,
    String currency
) {}
