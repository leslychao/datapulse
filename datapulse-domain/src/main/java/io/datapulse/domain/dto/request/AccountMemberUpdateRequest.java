package io.datapulse.domain.dto.request;

import static io.datapulse.domain.ValidationKeys.ACCOUNT_MEMBER_ROLE_REQUIRED;
import static io.datapulse.domain.ValidationKeys.ACCOUNT_MEMBER_STATUS_REQUIRED;

import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import jakarta.validation.constraints.NotNull;

public record AccountMemberUpdateRequest(
    @NotNull(message = ACCOUNT_MEMBER_ROLE_REQUIRED)
    AccountMemberRole role,

    @NotNull(message = ACCOUNT_MEMBER_STATUS_REQUIRED)
    AccountMemberStatus status
) {

}
