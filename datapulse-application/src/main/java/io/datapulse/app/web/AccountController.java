package io.datapulse.app.web;

import io.datapulse.domain.model.Account;
import io.datapulse.domain.port.PersistencePort;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final PersistencePort persistencePort;

  @PostMapping
  public ResponseEntity<Account> create(@Valid @RequestBody Account request) {
    Account saved = persistencePort.saveAccount(request);
    return ResponseEntity.created(URI.create("/api/accounts/" + saved.getId())).body(saved);
  }
}
