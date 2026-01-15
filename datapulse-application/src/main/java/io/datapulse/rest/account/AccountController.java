package io.datapulse.rest.account;

import io.datapulse.core.service.account.AccountService;
import io.datapulse.domain.request.account.AccountCreateRequest;
import io.datapulse.domain.request.account.AccountUpdateRequest;
import io.datapulse.domain.response.account.AccountResponse;
import io.datapulse.facade.AccountOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;
  private final AccountOnboardingService onboardingService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse create(@RequestBody AccountCreateRequest request) {
    return onboardingService.createAccount(request);
  }

  @PutMapping(path = "/{id}", consumes = "application/json")
  public AccountResponse update(
      @PathVariable Long id,
      @RequestBody AccountUpdateRequest request) {
    return accountService.updateFromRequest(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    accountService.delete(id);
  }
}
