package io.datapulse.rest;

import io.datapulse.core.service.AccountMemberService;
import io.datapulse.domain.dto.request.AccountMemberCreateRequest;
import io.datapulse.domain.dto.request.AccountMemberUpdateRequest;
import io.datapulse.domain.dto.response.AccountMemberResponse;
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
@RequestMapping(value = "/api/account-members", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountMemberController {

  private final AccountMemberService accountMemberService;

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public AccountMemberResponse create(@RequestBody AccountMemberCreateRequest request) {
    return accountMemberService.createFromRequest(request);
  }

  @PutMapping(path = "/{id}", consumes = "application/json")
  public AccountMemberResponse update(
      @PathVariable Long id,
      @RequestBody AccountMemberUpdateRequest request
  ) {
    return accountMemberService.updateFromRequest(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    accountMemberService.delete(id);
  }
}
