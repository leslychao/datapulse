package io.datapulse.core.service.inventory;

import io.datapulse.core.mapper.inventory.InventorySnapshotMapper;
import io.datapulse.core.repository.inventory.FactInventorySnapshotRepository;
import io.datapulse.domain.dto.inventory.InventorySnapshotDto;
import io.datapulse.domain.dto.request.inventory.InventorySnapshotQueryRequest;
import io.datapulse.domain.dto.response.inventory.InventorySnapshotResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventorySnapshotService {

  private static final LocalDate DEFAULT_FROM_DATE = LocalDate.of(2000, 1, 1);
  private static final LocalDate DEFAULT_TO_DATE   = LocalDate.of(2100, 12, 31);

  private final FactInventorySnapshotRepository repository;
  private final InventorySnapshotMapper mapper;

  public Page<InventorySnapshotResponse> findSnapshots(
      InventorySnapshotQueryRequest request,
      Pageable pageable
  ) {
    LocalDate from = request.fromDate() != null ? request.fromDate() : DEFAULT_FROM_DATE;
    LocalDate to   = request.toDate()   != null ? request.toDate()   : DEFAULT_TO_DATE;

    if (from.isAfter(to)) {
      throw new IllegalArgumentException("fromDate must be before or equal to toDate");
    }

    Page<InventorySnapshotDto> page = repository
        .findByAccountIdAndSourcePlatformIgnoreCaseAndSnapshotDateBetween(
            request.accountId(),
            request.marketplace().tag(),
            from,
            to,
            pageable
        )
        .map(mapper::toDto);

    return page.map(mapper::toResponse);
  }
}

