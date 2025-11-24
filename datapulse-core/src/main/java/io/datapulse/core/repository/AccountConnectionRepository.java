package io.datapulse.core.repository;

import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.domain.MarketplaceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountConnectionRepository extends JpaRepository<AccountConnectionEntity, Long> {

  Optional<AccountConnectionEntity> findByAccount_IdAndMarketplaceAndActiveTrue(
      Long accountId,
      MarketplaceType marketplaceType);

  boolean existsByAccount_IdAndMarketplace(Long accountId, MarketplaceType marketplace);

  boolean existsByAccount_IdAndMarketplaceAndIdNot(
      Long accountId,
      MarketplaceType marketplace,
      Long id);

  List<AccountConnectionEntity> findAllByAccount_IdAndActiveTrue(Long accountId);
}
