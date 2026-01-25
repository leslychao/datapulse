package io.datapulse.domain.request.account.invite;

import io.datapulse.domain.AccountMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AccountInviteCreateRequest(
    @NotNull @Email String email,
    @NotNull AccountMemberRole initialRole,
    @NotNull List<Long> accountIds
) {

}
