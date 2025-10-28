package io.datapulse.marketplaces.mapper.wb;

import io.datapulse.domain.OperationType;
import io.datapulse.domain.dto.FinanceDto;
import io.datapulse.marketplaces.dto.raw.wb.WbFinanceRaw; // = бывший WbRealizationRowRaw
import org.mapstruct.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring", imports = {BigDecimal.class, OffsetDateTime.class, ZoneId.class})
public abstract class WbFinanceMapper {

  @Mapping(target = "operationId", expression = "java(String.valueOf(src.rrd_id()))")
  @Mapping(target = "operationType", expression = "java(toOperationType(src.supplier_oper_name(), src.doc_type_name()))")
  @Mapping(target = "operationDate", expression = "java(pickOperationDate(src))")
  @Mapping(target = "postingNumber", source = "srid")
  @Mapping(target = "amountTotal", source = "ppvz_for_pay")
  @Mapping(target = "commissionAmount", source = "ppvz_sales_commission")
  @Mapping(target = "deliveryAmount", source = "delivery_rub")
  @Mapping(target = "storageFeeAmount", source = "storage_fee")
  @Mapping(target = "penaltyAmount", source = "penalty")
  @Mapping(target = "marketingAmount", expression = "java(safeSum(src.supplier_promo(), src.product_discount_for_report()))")
  @Mapping(target = "currency", source = "currency_name")
  public abstract FinanceDto toDto(WbFinanceRaw src);

  protected OperationType toOperationType(String supplierOperName, String docTypeName) {
    // Пример нормализации (подстрой под свой enum OperationType):
    if (supplierOperName == null && docTypeName == null) return OperationType.OTHER;
    String key = ((supplierOperName != null) ? supplierOperName : docTypeName).toLowerCase();
    if (key.contains("реализация") || key.contains("продажа")) return OperationType.SALE;
    if (key.contains("возврат")) return OperationType.RETURN;
    if (key.contains("штраф")) return OperationType.PENALTY;
    if (key.contains("хранен")) return OperationType.STORAGE;
    if (key.contains("достав")) return OperationType.DELIVERY;
    if (key.contains("маркет") || key.contains("акция") || key.contains("промо")) return OperationType.MARKETING;
    return OperationType.OTHER;
  }

  protected OffsetDateTime pickOperationDate(WbFinanceRaw s) {
    if (s.sale_dt() != null) return s.sale_dt();
    // rr_dt — LocalDate → в 00:00:00 по МСК
    if (s.rr_dt() != null) return s.rr_dt().atStartOfDay(ZoneId.of("Europe/Moscow")).toOffsetDateTime();
    if (s.order_dt() != null) return s.order_dt();
    return null;
  }

  protected BigDecimal safeSum(BigDecimal a, BigDecimal b) {
    return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
  }
}
