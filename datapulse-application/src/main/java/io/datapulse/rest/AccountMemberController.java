package io.datapulse.rest;

import io.datapulse.core.service.AccountMemberService;
import io.datapulse.domain.request.AccountMemberCreateRequest;
import io.datapulse.domain.request.AccountMemberUpdateRequest;
import io.datapulse.domain.response.AccountMemberResponse;
import io.datapulse.iam.DomainUserContext;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/accounts/{accountId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountMemberController {

  private final AccountMemberService accountMemberService;
  private final DomainUserContext domainUserContext;

  @GetMapping
  @PreAuthorize("@accountAccessService.canManageMembers(#accountId)")
  public List<AccountMemberResponse> getAll(@PathVariable long accountId) {
    return accountMemberService.getAllByAccountId(accountId);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@accountAccessService.canManageMembers(#accountId)")
  public AccountMemberResponse create(
      @PathVariable long accountId,
      @NotNull @RequestBody AccountMemberCreateRequest request
  ) {
    long actorProfileId = domainUserContext.requireProfileId();
    return accountMemberService.createMember(accountId, actorProfileId, request);
  }

  @PutMapping(path = "/{memberId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("@accountAccessService.canManageMembers(#accountId)")
  public AccountMemberResponse update(
      @PathVariable long accountId,
      @PathVariable long memberId,
      @NotNull @RequestBody AccountMemberUpdateRequest request
  ) {
    long actorProfileId = domainUserContext.requireProfileId();
    return accountMemberService.updateMember(accountId, memberId, actorProfileId, request);
  }

  @DeleteMapping("/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@accountAccessService.canManageMembers(#accountId)")
  public void delete(
      @PathVariable long accountId,
      @PathVariable long memberId
  ) {
    accountMemberService.deleteMember(accountId, memberId);
  }
}
