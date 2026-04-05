package io.datapulse.etl.persistence.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostProfileRow {

    private Long id;
    private long sellerSkuId;
    private String skuCode;
    private String productName;
    private BigDecimal costPrice;
    private String currency;
    private LocalDate validFrom;
    private LocalDate validTo;
    private long updatedByUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
