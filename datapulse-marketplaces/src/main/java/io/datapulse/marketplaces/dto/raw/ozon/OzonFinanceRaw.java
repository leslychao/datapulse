package io.datapulse.marketplaces.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceRaw(
    String operation_id,
    String posting_number,
    String operation_type, // SALE/RETURN/DELIVERY/...
    OffsetDateTime operation_date,
    BigDecimal amount_total,
    BigDecimal commission_amount,
    BigDecimal delivery_amount,
    BigDecimal storage_fee_amount,
    BigDecimal penalty_amount,
    BigDecimal marketing_amount,
    String currency
) {}
