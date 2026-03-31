package io.datapulse.tenancy.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import io.datapulse.tenancy.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_user")
public class AppUserEntity extends BaseEntity {

    @Column(name = "external_id", nullable = false, unique = true, length = 120)
    private String externalId;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;
}
