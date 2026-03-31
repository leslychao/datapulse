package io.datapulse.etl.persistence.canonical;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "canonical_finance_entry")
public class CanonicalFinanceEntryEntity extends BaseEntity {

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "source_platform", nullable = false, length = 10)
    private String sourcePlatform;

    @Column(name = "external_entry_id", nullable = false, length = 120)
    private String externalEntryId;

    @Column(name = "entry_type", nullable = false, length = 60)
    private String entryType;

    @Column(name = "posting_id", length = 120)
    private String postingId;

    @Column(name = "order_id", length = 120)
    private String orderId;

    @Column(name = "seller_sku_id")
    private Long sellerSkuId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "revenue_amount", nullable = false)
    private BigDecimal revenueAmount;

    @Column(name = "marketplace_commission_amount", nullable = false)
    private BigDecimal marketplaceCommissionAmount;

    @Column(name = "acquiring_commission_amount", nullable = false)
    private BigDecimal acquiringCommissionAmount;

    @Column(name = "logistics_cost_amount", nullable = false)
    private BigDecimal logisticsCostAmount;

    @Column(name = "storage_cost_amount", nullable = false)
    private BigDecimal storageCostAmount;

    @Column(name = "penalties_amount", nullable = false)
    private BigDecimal penaltiesAmount;

    @Column(name = "acceptance_cost_amount", nullable = false)
    private BigDecimal acceptanceCostAmount;

    @Column(name = "marketing_cost_amount", nullable = false)
    private BigDecimal marketingCostAmount;

    @Column(name = "other_marketplace_charges_amount", nullable = false)
    private BigDecimal otherMarketplaceChargesAmount;

    @Column(name = "compensation_amount", nullable = false)
    private BigDecimal compensationAmount;

    @Column(name = "refund_amount", nullable = false)
    private BigDecimal refundAmount;

    @Column(name = "net_payout")
    private BigDecimal netPayout;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "entry_date", nullable = false)
    private OffsetDateTime entryDate;

    @Column(name = "attribution_level", nullable = false, length = 10)
    private String attributionLevel;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
