package io.datapulse.iam.authorization;

import io.datapulse.domain.request.account.invite.AccountInviteCreateRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InviteAuthorizationService {

  private final AccountAccessService accountAccessService;

  public boolean canCreateInvite(AccountInviteCreateRequest request) {
    if (request == null || request.accountIds() == null) {
      return false;
    }

    request.accountIds().stream()
        .filter(Objects::nonNull)
        .distinct()
        .forEach(accountAccessService::requireManageMembers);

    return true;
  }
}
