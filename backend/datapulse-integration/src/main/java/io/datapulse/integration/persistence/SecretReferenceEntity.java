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
@Table(name = "secret_reference")
public class SecretReferenceEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "vault_path", nullable = false, length = 500)
    private String vaultPath;

    @Column(name = "vault_key", nullable = false, length = 120)
    private String vaultKey;

    @Column(name = "vault_version")
    private Integer vaultVersion;

    @Column(name = "secret_type", nullable = false, length = 40)
    private String secretType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;
}
