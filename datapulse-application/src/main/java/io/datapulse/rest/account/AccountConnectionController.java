package io.datapulse.rest.account;

import io.datapulse.core.service.account.AccountConnectionService;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping(value = "/api/account-connections", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountConnectionController {

  private final AccountConnectionService accountConnectionService;

  @GetMapping("/accounts/{accountId}")
  public List<AccountConnectionResponse> getByAccount(
      @PathVariable Long accountId
  ) {
    return accountConnectionService.getAllByAccountId(accountId);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AccountConnectionResponse create(
      @Valid @RequestBody AccountConnectionCreateRequest request) {
    return accountConnectionService.createFromRequest(request);
  }

  @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AccountConnectionResponse update(
      @PathVariable Long id,
      @Valid @RequestBody AccountConnectionUpdateRequest request) {
    return accountConnectionService.updateFromRequest(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    accountConnectionService.delete(id);
  }
}
