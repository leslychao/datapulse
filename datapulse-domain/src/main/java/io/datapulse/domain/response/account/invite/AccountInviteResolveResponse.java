package io.datapulse.domain.response.account.invite;

import java.time.OffsetDateTime;
import java.util.List;

public record AccountInviteResolveResponse(
    ResolveState state,
    String email,
    OffsetDateTime expiresAt,
    List<Target> targets
) {

  public enum ResolveState {
    PENDING,
    INVALID,
    EXPIRED,
    CANCELLED,
    ALREADY_ACCEPTED,
    ANONYMOUS_NEED_AUTH,
    AUTHENTICATED_EMAIL_MISMATCH,
    AUTHENTICATED_ALREADY_MEMBER,
    AUTHENTICATED_CAN_ACCEPT
  }

  public record Target(
      long accountId,
      String initialRole
  ) {

  }
}
