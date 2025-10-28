package io.datapulse.marketplaces.mapper.ozon;

import io.datapulse.domain.OperationType;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.marketplaces.dto.raw.ozon.OzonFinanceRaw;

public final class OzonFinanceMapper {
  private OzonFinanceMapper() {}

  public static FinanceDto toDto(OzonFinanceRaw r) {
    return new FinanceDto(
        r.operation_id(),
        toOp(r.operation_type()),
        r.operation_date(),
        r.posting_number(),
        r.amount_total(),
        r.commission_amount(),
        r.delivery_amount(),
        r.storage_fee_amount(),
        r.penalty_amount(),
        r.marketing_amount(),
        r.currency()
    );
  }

  private static OperationType toOp(String t){
    if (t==null) return OperationType.OTHER;
    try { return OperationType.valueOf(t.toUpperCase()); }
    catch (Exception ignored){ return OperationType.OTHER; }
  }
}
