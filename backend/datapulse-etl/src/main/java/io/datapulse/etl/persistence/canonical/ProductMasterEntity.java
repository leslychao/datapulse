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
@Table(name = "product_master")
public class ProductMasterEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "external_code", nullable = false, length = 120)
    private String externalCode;

    @Column(length = 500)
    private String name;

    @Column(length = 255)
    private String brand;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
