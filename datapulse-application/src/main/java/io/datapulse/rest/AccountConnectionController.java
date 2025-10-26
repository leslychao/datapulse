package io.datapulse.rest;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/account-connections", produces = "application/json")
@RequiredArgsConstructor
public class AccountConnectionController {

  private final AccountConnectionService service;

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public AccountConnectionResponse create(@Valid @RequestBody AccountConnectionCreateRequest req) {
    return service.create(req);
  }
}
