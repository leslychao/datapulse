package io.datapulse.core.repository;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.domain.MarketplaceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EtlExecutionAuditRepository
    extends JpaRepository<EtlExecutionAuditEntity, Long> {

  Optional<EtlExecutionAuditEntity> findTopByAccountIdAndMarketplaceAndSourceIdAndDateFromAndDateToOrderByCreatedAtDesc(
      long accountId,
      MarketplaceType marketplace,
      String sourceId,
      LocalDate dateFrom,
      LocalDate dateTo
  );

  @Query("""
      select case when count(a) > 0 then true else false end
      from EtlExecutionAuditEntity a
      where a.accountId   = :accountId
        and a.marketplace = :marketplace
        and a.event       = :event
        and a.sourceId in :sourceIds
      """)
  boolean existsExecutionForSources(
      long accountId,
      MarketplaceType marketplace,
      String event,
      List<String> sourceIds
  );
}
