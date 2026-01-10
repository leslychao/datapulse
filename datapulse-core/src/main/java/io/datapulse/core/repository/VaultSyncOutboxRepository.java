package io.datapulse.core.repository;

import io.datapulse.core.entity.account.VaultSyncCommandType;
import io.datapulse.core.entity.account.VaultSyncOutboxEntity;
import io.datapulse.core.entity.account.VaultSyncStatus;
import io.datapulse.domain.MarketplaceType;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VaultSyncOutboxRepository extends JpaRepository<VaultSyncOutboxEntity, Long> {

  Optional<VaultSyncOutboxEntity> findByAccountIdAndMarketplaceAndCommandType(
      Long accountId,
      MarketplaceType marketplace,
      VaultSyncCommandType commandType
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from VaultSyncOutboxEntity o "
          + "where o.status in :statuses "
          + "and (o.nextAttemptAt is null or o.nextAttemptAt <= :now) "
          + "order by o.createdAt"
  )
  List<VaultSyncOutboxEntity> lockNextBatch(
      @Param("statuses") Set<VaultSyncStatus> statuses,
      @Param("now") OffsetDateTime now,
      Pageable pageable
  );
}
