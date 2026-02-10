package io.datapulse.rest.invite;

import io.datapulse.core.service.account.invite.AccountInviteService;
import io.datapulse.domain.request.account.invite.AccountInviteAcceptRequest;
import io.datapulse.domain.request.account.invite.AccountInviteCreateRequest;
import io.datapulse.domain.response.account.invite.AccountInviteAcceptResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResolveResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResponse;
import io.datapulse.iam.DomainUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public AccountInviteResponse create(@RequestBody @Valid AccountInviteCreateRequest request) {
    long currentProfileId = domainUserContext.requireProfileId();
    return accountInviteService.createInvite(currentProfileId, request);
  }

  @GetMapping("/resolve")
  public AccountInviteResolveResponse resolve(@RequestParam("token") String token) {
    return domainUserContext.getProfileId()
        .map(profileId -> accountInviteService.resolveForAuthenticatedUser(
            profileId,
            domainUserContext.requireCurrentEmail(),
            token
        ))
        .orElseGet(() -> accountInviteService.resolve(token));
  }

  @PostMapping(path = "/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("isAuthenticated()")
  public AccountInviteAcceptResponse accept(
      @RequestBody @Valid AccountInviteAcceptRequest request) {
    long currentProfileId = domainUserContext.requireProfileId();
    String currentEmail = domainUserContext.requireCurrentEmail();

    return accountInviteService.accept(currentProfileId, currentEmail, request);
  }
}
