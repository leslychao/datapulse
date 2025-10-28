package io.datapulse.marketplaces.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceRaw(
    Result result
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      List<Operation> operations,
      Boolean has_next
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Operation(
      String operation_id,
      String operation_type,           // например, OperationMarketplaceService, OperationClaim и т.п.
      OffsetDateTime operation_date,
      Posting posting,
      Amounts amounts,                 // агрегаты по начислению/удержанию (если есть)
      Services services                // разрез по услугам/комиссиям (если есть)
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Posting(
      String posting_number,
      String delivery_schema,          // FBO/FBS/rFBS
      Long warehousе_id
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Amounts(
      BigDecimal accrual,
      BigDecimal charge,
      BigDecimal payout
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Services(
      BigDecimal marketplace_service_item_deliv_to_customer,
      BigDecimal marketplace_service_item_dropoff_pvz,
      BigDecimal marketplace_service_item_direct_flow_trans, // и др. сервисы; оставляем гибко
      BigDecimal item_promocode,
      BigDecimal sales_percent
  ) {}
}
