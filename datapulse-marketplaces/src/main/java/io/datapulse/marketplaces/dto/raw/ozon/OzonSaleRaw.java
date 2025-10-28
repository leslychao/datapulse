package io.datapulse.marketplaces.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonSaleRaw(
    String posting_number,
    String offer_id,
    String sku,
    Integer quantity,
    BigDecimal price,
    BigDecimal total_price,
    BigDecimal commission_amount,
    BigDecimal delivery_amount,
    String status,
    OffsetDateTime created_at,
    OffsetDateTime in_process_at,
    Boolean is_return,
    Boolean is_cancelled
) {}
