package io.datapulse.rest.inventory;

import io.datapulse.core.service.inventory.InventorySnapshotService;
import io.datapulse.domain.dto.request.inventory.InventorySnapshotQueryRequest;
import io.datapulse.domain.dto.response.inventory.InventorySnapshotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/inventory-snapshots",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class InventorySnapshotController {

  private final InventorySnapshotService inventorySnapshotService;

  @GetMapping
  public Page<InventorySnapshotResponse> list(
      @Valid InventorySnapshotQueryRequest request,
      Pageable pageable
  ) {
    return inventorySnapshotService.findSnapshots(request, pageable);
  }
}
