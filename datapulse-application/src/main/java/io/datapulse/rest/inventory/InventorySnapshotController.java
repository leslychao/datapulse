package io.datapulse.rest.inventory;

import io.datapulse.core.service.inventory.InventorySnapshotService;
import io.datapulse.domain.request.inventory.InventorySnapshotQueryRequest;
import io.datapulse.domain.response.inventory.InventorySnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/accounts/{accountId}/inventory-snapshots",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class InventorySnapshotController {

  private final InventorySnapshotService service;

  @GetMapping
  public Page<InventorySnapshotResponse> findSnapshots(
      @PathVariable
      Long accountId,
      InventorySnapshotQueryRequest request,
      Pageable pageable
  ) {
    return service.searchInventorySnapshots(accountId, request, pageable);
  }
}
