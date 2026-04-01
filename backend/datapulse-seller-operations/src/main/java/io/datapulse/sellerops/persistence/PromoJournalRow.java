package io.datapulse.sellerops.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoJournalRow {

    private long decisionId;
    private OffsetDateTime decisionDate;
    private String promoName;
    private String promoType;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String evaluationResult;
    private String participationDecision;
    private String actionStatus;
    private BigDecimal requiredPrice;
    private BigDecimal marginAtPromoPrice;
    private BigDecimal marginDeltaPct;
    private String explanationSummary;
}
