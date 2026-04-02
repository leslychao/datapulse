package io.datapulse.analytics.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PostingPnlDetailResponse(
    String postingId,
    String skuCode,
    String productName,
    String sourcePlatform,
    LocalDate financeDate,
    BigDecimal revenueAmount,
    BigDecimal totalCostsAmount,
    BigDecimal netPayout,
    BigDecimal netCogs,
    BigDecimal reconciliationResidual,
    List<PostingDetailResponse> entries
) {}
