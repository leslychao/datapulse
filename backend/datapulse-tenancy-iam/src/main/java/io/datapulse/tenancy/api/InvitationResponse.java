package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.InvitationStatus;
import io.datapulse.tenancy.domain.MemberRole;

import java.time.OffsetDateTime;

public record InvitationResponse(
        long id,
        String email,
        MemberRole role,
        InvitationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {}
