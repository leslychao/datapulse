package io.datapulse.domain.request.account.invite;

import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.NotBlank;

public record AccountInviteAcceptRequest(
    @NotBlank(message = ValidationKeys.INVITE_TOKEN_REQUIRED) String token
) {

}
