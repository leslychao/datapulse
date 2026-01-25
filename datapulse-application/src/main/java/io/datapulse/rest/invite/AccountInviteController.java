package io.datapulse.rest.invite;

import io.datapulse.core.service.account.invite.AccountInviteService;
import io.datapulse.domain.exception.SecurityException;
import io.datapulse.domain.identity.AuthenticatedUser;
import io.datapulse.domain.request.account.invite.AccountInviteAcceptRequest;
import io.datapulse.domain.request.account.invite.AccountInviteCreateRequest;
import io.datapulse.domain.response.account.invite.AccountInviteAcceptResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResolveResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResponse;
import io.datapulse.iam.DomainUserContext;
import io.datapulse.security.SecurityHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/invites", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountInviteController {

  private final AccountInviteService accountInviteService;
  private final DomainUserContext domainUserContext;
  private final SecurityHelper securityHelper;

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AccountInviteResponse create(@RequestBody @Valid AccountInviteCreateRequest request) {
    long currentProfileId = domainUserContext.requireProfileId();

    String currentEmail = securityHelper.getCurrentUserIfAuthenticated()
        .map(AuthenticatedUser::email)
        .orElseThrow(SecurityException::userProfileNotResolved);

    return accountInviteService.createInvite(currentProfileId, currentEmail, request);
  }

  @GetMapping("/resolve")
  public AccountInviteResolveResponse resolve(@RequestParam("token") String token) {
    return securityHelper.getCurrentUserIfAuthenticated()
        .map(user -> accountInviteService.resolveForAuthenticatedUser(
            domainUserContext.requireProfileId(),
            user.email(),
            token
        ))
        .orElseGet(() -> accountInviteService.resolve(token));
  }

  @PostMapping(path = "/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AccountInviteAcceptResponse accept(@RequestBody @Valid AccountInviteAcceptRequest request) {
    long currentProfileId = domainUserContext.requireProfileId();

    String currentEmail = securityHelper.getCurrentUserIfAuthenticated()
        .map(AuthenticatedUser::email)
        .orElseThrow(SecurityException::userProfileNotResolved);

    return accountInviteService.accept(currentProfileId, currentEmail, request);
  }
}
