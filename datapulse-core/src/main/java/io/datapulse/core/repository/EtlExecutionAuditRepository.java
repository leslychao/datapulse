package io.datapulse.core.repository;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.domain.MarketplaceType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlExecutionAuditRepository
    extends JpaRepository<EtlExecutionAuditEntity, Long> {

  Optional<EtlExecutionAuditEntity>
  findTopByAccountIdAndMarketplaceAndSourceIdAndDateFromAndDateToOrderByCreatedAtDesc(
      long accountId,
      MarketplaceType marketplace,
      String sourceId,
      LocalDate dateFrom,
      LocalDate dateTo
  );
}
