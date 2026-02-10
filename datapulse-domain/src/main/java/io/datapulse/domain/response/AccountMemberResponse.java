package io.datapulse.domain.response;

import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import java.time.OffsetDateTime;

public record AccountMemberResponse(
    Long id,
    String keycloakSub,
    Long accountId,
    Long userId,

    String email,
    String username,
    String fullName,

    boolean recentlyActive,
    OffsetDateTime lastActivityAt,

    AccountMemberRole role,
    AccountMemberStatus status,

    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}
