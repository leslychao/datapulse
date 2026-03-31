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
@Table(name = "warehouse")
public class WarehouseEntity extends BaseEntity {

    @Column(name = "marketplace_connection_id", nullable = false)
    private Long marketplaceConnectionId;

    @Column(name = "external_warehouse_id", nullable = false, length = 120)
    private String externalWarehouseId;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "warehouse_type", nullable = false, length = 20)
    private String warehouseType;

    @Column(name = "marketplace_type", nullable = false, length = 10)
    private String marketplaceType;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
