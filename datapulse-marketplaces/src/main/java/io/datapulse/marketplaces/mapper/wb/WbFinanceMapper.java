package io.datapulse.marketplaces.mapper.wb;

import io.datapulse.domain.OperationType;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.marketplaces.dto.raw.wb.WbFinanceRaw;

public final class WbFinanceMapper {
  private WbFinanceMapper() {}

  public static FinanceDto toDto(WbFinanceRaw r) {
    return new FinanceDto(
        r.operationId(),
        toOp(r.operationType()),
        r.operationDate(),
        r.rrn(), // или null — в зависимости от твоей связки
        r.amount(),
        r.commission(),
        r.delivery(),
        r.storageFee(),
        r.penalty(),
        r.marketing(),
        r.currency()
    );
  }

  private static OperationType toOp(String t){
    if (t==null) return OperationType.OTHER;
    try { return OperationType.valueOf(t.toUpperCase()); }
    catch (Exception ignored){ return OperationType.OTHER; }
  }
}
