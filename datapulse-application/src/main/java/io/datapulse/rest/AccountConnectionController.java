package io.datapulse.rest;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/account-connections", produces = "application/json")
@RequiredArgsConstructor
public class AccountConnectionController {

  private final AccountConnectionService accountConnectionService;

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public AccountConnectionResponse create(
      @Valid @RequestBody AccountConnectionCreateRequest request) {
    return accountConnectionService.create(request);
  }

  @PutMapping(value = "/{accountId}", consumes = "application/json")
  public AccountConnectionResponse update(
      @PathVariable Long accountId,
      @Valid @RequestBody AccountConnectionUpdateRequest request) {
    return accountConnectionService.update(accountId, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    accountConnectionService.delete(id);
  }
}
