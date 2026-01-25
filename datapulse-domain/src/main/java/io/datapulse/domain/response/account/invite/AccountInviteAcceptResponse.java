package io.datapulse.domain.response.account.invite;

import java.util.List;

public record AccountInviteAcceptResponse(
    boolean accepted,
    List<Long> grantedAccountIds
) {
}
