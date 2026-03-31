package io.datapulse.integration.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "marketplace_connection")
public class MarketplaceConnectionEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "marketplace_type", nullable = false, length = 10)
    private String marketplaceType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "external_account_id", length = 120)
    private String externalAccountId;

    @Column(name = "secret_reference_id", nullable = false)
    private Long secretReferenceId;

    @Column(name = "perf_secret_reference_id")
    private Long perfSecretReferenceId;

    @Column(name = "last_check_at")
    private OffsetDateTime lastCheckAt;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "last_error_at")
    private OffsetDateTime lastErrorAt;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;
}
