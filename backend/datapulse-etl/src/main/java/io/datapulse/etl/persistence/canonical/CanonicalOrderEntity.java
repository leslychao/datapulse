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
@Table(name = "canonical_order")
public class CanonicalOrderEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "source_platform", nullable = false, length = 10)
    private String sourcePlatform;

    @Column(name = "external_order_id", nullable = false, length = 120)
    private String externalOrderId;

    @Column(name = "marketplace_offer_id")
    private Long marketplaceOfferId;

    @Column(name = "order_date", nullable = false)
    private OffsetDateTime orderDate;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_per_unit", nullable = false)
    private BigDecimal pricePerUnit;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "fulfillment_type", length = 10)
    private String fulfillmentType;

    @Column(length = 255)
    private String region;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
