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
@Table(name = "canonical_return")
public class CanonicalReturnEntity extends BaseEntity {

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "source_platform", nullable = false, length = 10)
    private String sourcePlatform;

    @Column(name = "external_return_id", nullable = false, length = 120)
    private String externalReturnId;

    @Column(name = "canonical_order_id")
    private Long canonicalOrderId;

    @Column(name = "marketplace_offer_id")
    private Long marketplaceOfferId;

    @Column(name = "seller_sku_id")
    private Long sellerSkuId;

    @Column(name = "return_date", nullable = false)
    private OffsetDateTime returnDate;

    @Column(name = "return_amount", nullable = false)
    private BigDecimal returnAmount;

    @Column(name = "return_reason", length = 255)
    private String returnReason;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 30)
    private String status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "fulfillment_type", length = 10)
    private String fulfillmentType;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
