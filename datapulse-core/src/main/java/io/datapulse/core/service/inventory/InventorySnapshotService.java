package io.datapulse.core.service.inventory;

import io.datapulse.core.mapper.inventory.InventorySnapshotMapper;
import io.datapulse.core.repository.inventory.FactInventorySnapshotRepository;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.request.inventory.InventorySnapshotQueryRequest;
import io.datapulse.domain.response.inventory.InventorySnapshotResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class InventorySnapshotService {

  private static final LocalDate DEFAULT_FROM_DATE = LocalDate.of(2000, 1, 1);
  private static final LocalDate DEFAULT_TO_DATE = LocalDate.of(2100, 12, 31);

  private final FactInventorySnapshotRepository repository;
  private final InventorySnapshotMapper mapper;

  public Page<InventorySnapshotResponse> searchInventorySnapshots(
      @NotNull(message = ValidationKeys.ACCOUNT_ID_REQUIRED) Long accountId,
      @Valid @NotNull(message = ValidationKeys.REQUEST_REQUIRED) InventorySnapshotQueryRequest request,
      @NotNull(message = ValidationKeys.PAGEABLE_REQUIRED) Pageable pageable
  ) {
    LocalDate from = request.fromDate() != null
        ? request.fromDate()
        : DEFAULT_FROM_DATE;

    LocalDate to = request.toDate() != null
        ? request.toDate()
        : DEFAULT_TO_DATE;

    String sourcePlatform =
        request.marketplace() != null ? request.marketplace().tag() : null;

    return repository
        .findByAccountIdAndSourcePlatformIgnoreCaseAndSnapshotDateBetween(
            accountId,
            sourcePlatform,
            from,
            to,
            pageable
        )
        .map(mapper::toResponse);
  }
}
