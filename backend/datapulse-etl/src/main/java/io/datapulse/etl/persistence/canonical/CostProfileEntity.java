package io.datapulse.etl.persistence.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cost_profile")
public class CostProfileEntity extends BaseEntity {

    @Column(name = "seller_sku_id", nullable = false)
    private Long sellerSkuId;

    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
}
