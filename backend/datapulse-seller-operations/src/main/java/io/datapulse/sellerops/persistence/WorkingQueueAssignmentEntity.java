package io.datapulse.sellerops.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "working_queue_assignment")
@Getter
@Setter
public class WorkingQueueAssignmentEntity extends BaseEntity {

    @Column(name = "queue_definition_id", nullable = false)
    private Long queueDefinitionId;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(columnDefinition = "text")
    private String note;
}
