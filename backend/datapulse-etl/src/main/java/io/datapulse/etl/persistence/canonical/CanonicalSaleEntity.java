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
@Table(name = "canonical_sale")
public class CanonicalSaleEntity extends BaseEntity {

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "source_platform", nullable = false, length = 10)
    private String sourcePlatform;

    @Column(name = "external_sale_id", nullable = false, length = 120)
    private String externalSaleId;

    @Column(name = "canonical_order_id")
    private Long canonicalOrderId;

    @Column(name = "marketplace_offer_id")
    private Long marketplaceOfferId;

    @Column(name = "posting_id", length = 120)
    private String postingId;

    @Column(name = "seller_sku_id")
    private Long sellerSkuId;

    @Column(name = "sale_date", nullable = false)
    private OffsetDateTime saleDate;

    @Column(name = "sale_amount", nullable = false)
    private BigDecimal saleAmount;

    private BigDecimal commission;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
