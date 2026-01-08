package io.datapulse.domain.dto.request;

import static io.datapulse.domain.ValidationKeys.ACCOUNT_MEMBER_ROLE_REQUIRED;

import io.datapulse.domain.AccountMemberRole;
import jakarta.validation.constraints.NotNull;

public record AccountMemberUpdateRequest(
    @NotNull(message = ACCOUNT_MEMBER_ROLE_REQUIRED)
    AccountMemberRole role
) {

}
