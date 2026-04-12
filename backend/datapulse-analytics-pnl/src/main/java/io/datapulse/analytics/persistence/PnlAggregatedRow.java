package io.datapulse.analytics.persistence;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PnlAggregatedRow {

  private BigDecimal revenueAmount;
  private BigDecimal marketplaceCommissionAmount;
  private BigDecimal acquiringCommissionAmount;
  private BigDecimal logisticsCostAmount;
  private BigDecimal storageCostAmount;
  private BigDecimal penaltiesAmount;
  private BigDecimal marketingCostAmount;
  private BigDecimal acceptanceCostAmount;
  private BigDecimal otherMarketplaceChargesAmount;
  private BigDecimal compensationAmount;
  private BigDecimal refundAmount;
  private BigDecimal netPayout;
  private BigDecimal netCogs;
  private BigDecimal advertisingCost;
  private BigDecimal marketplacePnl;
  private BigDecimal fullPnl;
  private BigDecimal reconciliationResidual;
}
