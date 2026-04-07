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
@Table(name = "category")
public class CategoryEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "marketplace_connection_id", nullable = false)
    private Long marketplaceConnectionId;

    @Column(name = "external_category_id", nullable = false, length = 120)
    private String externalCategoryId;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "parent_category_id")
    private Long parentCategoryId;

    @Column(name = "marketplace_type", nullable = false, length = 10)
    private String marketplaceType;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;
}
