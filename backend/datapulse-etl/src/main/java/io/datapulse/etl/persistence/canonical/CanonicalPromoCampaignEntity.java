package io.datapulse.etl.persistence.canonical;

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
@Table(name = "canonical_promo_campaign")
public class CanonicalPromoCampaignEntity extends BaseEntity {

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Column(name = "external_promo_id", nullable = false, length = 120)
    private String externalPromoId;

    @Column(name = "source_platform", nullable = false, length = 10)
    private String sourcePlatform;

    @Column(name = "promo_name", nullable = false, length = 500)
    private String promoName;

    @Column(name = "promo_type", nullable = false, length = 60)
    private String promoType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "date_from")
    private OffsetDateTime dateFrom;

    @Column(name = "date_to")
    private OffsetDateTime dateTo;

    @Column(name = "freeze_at")
    private OffsetDateTime freezeAt;

    @Column(name = "participation_deadline")
    private OffsetDateTime participationDeadline;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 60)
    private String mechanic;

    @Column(name = "is_participating")
    private Boolean isParticipating;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;

    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;
}
