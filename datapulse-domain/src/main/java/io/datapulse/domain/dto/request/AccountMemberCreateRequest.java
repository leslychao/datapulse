package io.datapulse.domain.dto.request;

import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.NotNull;

public record AccountMemberCreateRequest(
    @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_ACCOUNT_ID_REQUIRED)
    Long accountId,

    @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_USER_ID_REQUIRED)
    Long userId,

    @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_ROLE_REQUIRED)
    AccountMemberRole role,

    @NotNull(message = ValidationKeys.ACCOUNT_MEMBER_STATUS_REQUIRED)
    AccountMemberStatus status
) {

}
