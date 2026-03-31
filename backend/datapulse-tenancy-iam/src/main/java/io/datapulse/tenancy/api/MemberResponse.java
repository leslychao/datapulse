package io.datapulse.tenancy.api;

import io.datapulse.tenancy.domain.MemberRole;
import io.datapulse.tenancy.domain.MemberStatus;

import java.time.OffsetDateTime;

public record MemberResponse(
        long userId,
        String email,
        String name,
        MemberRole role,
        MemberStatus status,
        OffsetDateTime createdAt
) {}
