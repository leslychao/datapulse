package io.datapulse.domain.request.account.invite;

import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AccountInviteCreateRequest(
    @NotBlank(message = ValidationKeys.INVITE_EMAIL_REQUIRED)
    @Email(message = ValidationKeys.INVITE_EMAIL_INVALID)
    String email,

    @NotNull(message = ValidationKeys.INVITE_INITIAL_ROLE_REQUIRED)
    AccountMemberRole initialRole,

    @NotEmpty(message = ValidationKeys.INVITE_ACCOUNTS_REQUIRED)
    List<
        @NotNull(message = ValidationKeys.INVITE_ACCOUNT_ID_REQUIRED)
        @Positive(message = ValidationKeys.ACCOUNT_ID_REQUIRED)
            Long
        > accountIds
) {

}
