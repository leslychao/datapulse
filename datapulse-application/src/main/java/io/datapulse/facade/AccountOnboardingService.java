package io.datapulse.facade;

import io.datapulse.core.service.AccountMemberService;
import io.datapulse.core.service.account.AccountService;
import io.datapulse.domain.request.account.AccountCreateRequest;
import io.datapulse.domain.response.account.AccountResponse;
import io.datapulse.iam.DomainUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountOnboardingService {

  private final AccountService accountService;
  private final AccountMemberService accountMemberService;
  private final DomainUserContext domainUserContext;

  @Transactional
  public AccountResponse createAccount(AccountCreateRequest request) {
    AccountResponse account = accountService.createFromRequest(request);

    long profileId = domainUserContext.requireProfileId();
    accountMemberService.ensureOwnerMembership(account.id(), profileId);

    return account;
  }
}
