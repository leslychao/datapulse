package io.datapulse.sellerops.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "working_queue_definition")
@Getter
@Setter
public class WorkingQueueDefinitionEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "queue_type", nullable = false, length = 30)
    private String queueType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auto_criteria", columnDefinition = "jsonb")
    private Map<String, Object> autoCriteria;

    @Column(nullable = false)
    private boolean enabled;
}
