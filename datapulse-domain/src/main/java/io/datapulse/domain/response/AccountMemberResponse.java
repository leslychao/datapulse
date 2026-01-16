package io.datapulse.domain.response;

import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import java.time.OffsetDateTime;

public record AccountMemberResponse(
    Long id,
    Long accountId,
    Long userId,
    AccountMemberRole role,
    AccountMemberStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

}
