package io.datapulse.rest.account;

import io.datapulse.core.service.account.AccountConnectionService;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.response.account.AccountConnectionResponse;
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
@RequestMapping(value = "/api/accounts/{accountId}/connections", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountConnectionController {

  private final AccountConnectionService accountConnectionService;

  @GetMapping
  @PreAuthorize("@accountAccessService.canRead(#accountId)")
  public List<AccountConnectionResponse> getByAccount(@PathVariable long accountId) {
    return accountConnectionService.getAllByAccountId(accountId);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@accountAccessService.canWrite(#accountId)")
  public AccountConnectionResponse create(
      @PathVariable long accountId,
      @RequestBody AccountConnectionCreateRequest request
  ) {
    return accountConnectionService.create(accountId, request);
  }

  @PutMapping(value = "/{connectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("@accountAccessService.canWrite(#accountId)")
  public AccountConnectionResponse update(
      @PathVariable long accountId,
      @PathVariable long connectionId,
      @RequestBody AccountConnectionUpdateRequest request
  ) {
    return accountConnectionService.update(accountId, connectionId, request);
  }

  @DeleteMapping("/{connectionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@accountAccessService.canWrite(#accountId)")
  public void delete(
      @PathVariable long accountId,
      @PathVariable long connectionId
  ) {
    accountConnectionService.delete(accountId, connectionId);
  }
}
