package io.datapulse.core.repository.inventory;

import io.datapulse.core.entity.inventory.FactInventorySnapshotEntity;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactInventorySnapshotRepository
    extends JpaRepository<FactInventorySnapshotEntity, Long> {

  Page<FactInventorySnapshotEntity> findByAccountIdAndSourcePlatformIgnoreCaseAndSnapshotDateBetween(
      Long accountId,
      String sourcePlatform,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable
  );
}
