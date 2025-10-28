package io.datapulse.marketplaces.mapper.ozon;

import io.datapulse.domain.OperationType;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.marketplaces.dto.raw.ozon.OzonFinanceRaw;
import org.mapstruct.*;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", imports = BigDecimal.class)
public abstract class OzonFinanceMapper {

  /** Маппим один operation-элемент в FinanceDto */
  @Mappings({
      @Mapping(target = "operationId", source = "operation.operation_id"),
      @Mapping(target = "operationType", expression = "java(toOperationType(operation.operation_type()))"),
      @Mapping(target = "operationDate", source = "operation.operation_date"),
      @Mapping(target = "postingNumber", source = "operation.posting.posting_number"),
      @Mapping(target = "amountTotal", source = "operation.amounts.payout"),
      @Mapping(target = "commissionAmount", expression = "java(sumServices(operation.services()))"),
      @Mapping(target = "deliveryAmount", expression = "java(extractDelivery(operation.services()))"),
      @Mapping(target = "storageFeeAmount", constant = "0"),
      @Mapping(target = "penaltyAmount", expression = "java(extractPenalty(operation))"),
      @Mapping(target = "marketingAmount", expression = "java(extractMarketing(operation.services()))"),
      @Mapping(target = "currency", constant = "RUR")
  })
  public abstract FinanceDto toDto(OzonFinanceRaw.Operation operation);

  protected OperationType toOperationType(String operationType) {
    if (operationType == null) return OperationType.OTHER;
    String key = operationType.toLowerCase();
    if (key.contains("marketplace")) return OperationType.SALE;
    if (key.contains("claim")) return OperationType.PENALTY;
    return OperationType.OTHER;
  }

  protected BigDecimal sumServices(OzonFinanceRaw.Services s) {
    if (s == null) return BigDecimal.ZERO;
    return BigDecimal.ZERO
        .add(nz(s.marketplace_service_item_deliv_to_customer()))
        .add(nz(s.marketplace_service_item_dropoff_pvz()))
        .add(nz(s.marketplace_service_item_direct_flow_trans()))
        .add(nz(s.item_promocode()))
        .add(nz(s.sales_percent()));
  }

  protected BigDecimal extractDelivery(OzonFinanceRaw.Services s) {
    if (s == null) return BigDecimal.ZERO;
    return nz(s.marketplace_service_item_deliv_to_customer())
        .add(nz(s.marketplace_service_item_direct_flow_trans()));
  }

  protected BigDecimal extractMarketing(OzonFinanceRaw.Services s) {
    if (s == null) return BigDecimal.ZERO;
    return nz(s.item_promocode()).add(nz(s.sales_percent()));
  }

  protected BigDecimal extractPenalty(OzonFinanceRaw.Operation op) {
    // В Ozon штрафы обычно в charge/operation_type=OperationClaim
    BigDecimal charge = op.amounts() != null ? nz(op.amounts().charge()) : BigDecimal.ZERO;
    if (toOperationType(op.operation_type()) == OperationType.PENALTY) return charge;
    return BigDecimal.ZERO;
  }

  protected BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
