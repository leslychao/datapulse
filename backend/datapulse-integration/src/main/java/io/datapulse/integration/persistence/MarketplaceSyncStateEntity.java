package io.datapulse.integration.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "marketplace_sync_state")
public class MarketplaceSyncStateEntity extends BaseEntity {

    @Column(name = "marketplace_connection_id", nullable = false)
    private Long marketplaceConnectionId;

    @Column(name = "data_domain", nullable = false, length = 40)
    private String dataDomain;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "next_scheduled_at")
    private OffsetDateTime nextScheduledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sync_cursor", columnDefinition = "jsonb")
    private String syncCursor;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
