package io.datapulse.sellerops.domain;

import java.math.BigDecimal;
import java.util.List;

public record GridFilter(
    List<String> marketplaceType,
    List<Long> connectionId,
    List<String> status,
    String skuCode,
    String productName,
    List<Long> categoryId,
    BigDecimal marginMin,
    BigDecimal marginMax,
    Boolean hasManualLock,
    Boolean hasActivePromo,
    String lastDecision,
    String lastActionStatus,
    Long viewId,
    String stockRisk
) {}
