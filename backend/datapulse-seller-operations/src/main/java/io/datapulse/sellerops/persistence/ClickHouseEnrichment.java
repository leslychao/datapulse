package io.datapulse.sellerops.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickHouseEnrichment {

    private long offerId;
    private BigDecimal daysOfCover;
    private String stockRisk;
    private BigDecimal revenue30d;
    private BigDecimal netPnl30d;
    private BigDecimal velocity14d;
    private BigDecimal returnRatePct;
    private BigDecimal adSpend30d;
    private BigDecimal drr30dPct;
    private BigDecimal adCpo;
    private BigDecimal adRoas;
}
