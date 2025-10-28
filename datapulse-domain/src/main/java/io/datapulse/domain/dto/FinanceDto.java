package io.datapulse.domain.dto;

import io.datapulse.domain.OperationType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceDto extends LongBaseDto {

  /** Идентификатор операции в источнике (Ozon: operation_id; WB: rrd_id) */
  private String operationId;

  /** Нормализованный тип операции (из supplier_oper_name/doc_type_name у WB; из operation_type у Ozon) */
  private OperationType operationType;

  /** Дата операции (Ozon: operation_date; WB: rr_dt/sale_dt/order_dt — берем sale_dt при наличии, иначе rr_dt) */
  private OffsetDateTime operationDate;

  /** Номер отправления/заказа (Ozon: posting.posting_number; WB: srid или zk/shkId при наличии) */
  private String postingNumber;

  /** Итого к выплате/списанию (Ozon: amounts.payout; WB: ppvz_for_pay) */
  private BigDecimal amountTotal;

  /** Комиссии маркетплейса (Ozon: services.* агрегировано; WB: ppvz_sales_commission) */
  private BigDecimal commissionAmount;

  /** Доставка/логистика (Ozon: services.marketplace_service_*; WB: delivery_rub) */
  private BigDecimal deliveryAmount;

  /** Хранение (Ozon: отдельного поля нет → 0; WB: storage_fee) */
  private BigDecimal storageFeeAmount;

  /** Штрафы (Ozon: OperationClaim/… в amounts.charge; WB: penalty) */
  private BigDecimal penaltyAmount;

  /** Маркетинг/промо (Ozon: item_promocode/sales_percent и т.п.; WB: supplier_promo + product_discount_for_report) */
  private BigDecimal marketingAmount;

  /** Валюта (Ozon: RUR; WB: currency_name, как правило RUB) */
  private String currency;
}
