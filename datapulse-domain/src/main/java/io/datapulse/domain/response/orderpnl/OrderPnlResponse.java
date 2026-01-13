package io.datapulse.domain.response.orderpnl;

import io.datapulse.domain.MarketplaceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record OrderPnlResponse(

    Long accountId,
    MarketplaceType sourcePlatform,
    String orderId,

    String currency,

    LocalDate firstFinanceDate,
    LocalDate lastFinanceDate,

    BigDecimal revenueGross,
    BigDecimal marketplaceCommissionAmount,
    BigDecimal logisticsCostAmount,
    BigDecimal penaltiesAmount,
    BigDecimal refundAmount,
    BigDecimal netPayout,
    BigDecimal pnlAmount,

    int itemsSoldCount,
    int returnedItemsCount,

    boolean isReturned,
    boolean hasPenalties,

    OffsetDateTime updatedAt
) {

}
