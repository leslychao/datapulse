package io.datapulse.analytics.api;

import io.datapulse.analytics.domain.MaterializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/admin/materialization",
    produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MaterializationController {

  private final MaterializationService materializationService;

  @PostMapping("/full")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@workspaceAccessService.isAdminOrOwner()")
  public void runFull() {
    materializationService.runFullRematerialization();
  }
}
