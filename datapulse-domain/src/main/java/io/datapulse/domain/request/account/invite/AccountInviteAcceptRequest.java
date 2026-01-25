package io.datapulse.domain.request.account.invite;

import jakarta.validation.constraints.NotBlank;

public record AccountInviteAcceptRequest(
    @NotBlank String token
) {

}
