package io.datapulse.domain.response.account.invite;

import io.datapulse.domain.AccountInviteStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record AccountInviteResponse(
    long inviteId,
    AccountInviteStatus status,
    String email,
    OffsetDateTime expiresAt,
    List<Target> targets
) {

  public record Target(
      long accountId,
      String initialRole
  ) {

  }
}
