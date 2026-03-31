package io.datapulse.etl.persistence.canonical;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "seller_sku")
public class SellerSkuEntity extends BaseEntity {

    @Column(name = "product_master_id", nullable = false)
    private Long productMasterId;

    @Column(name = "sku_code", nullable = false, length = 120)
    private String skuCode;

    @Column(length = 120)
    private String barcode;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
