package io.datapulse.sellerops.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceJournalRow {

    private long decisionId;
    private OffsetDateTime decisionDate;
    private String decisionType;
    private String skipReason;
    private String policyName;
    private int policyVersion;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private BigDecimal priceChangePct;
    private String actionStatus;
    private String executionMode;
    private BigDecimal actualPrice;
    private String reconciliationSource;
    private String explanationSummary;
}
