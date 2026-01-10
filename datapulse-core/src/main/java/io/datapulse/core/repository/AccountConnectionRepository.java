package io.datapulse.core.repository;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.domain.MarketplaceType;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountConnectionRepository extends JpaRepository<AccountConnectionEntity, Long> {

  boolean existsByAccount_IdAndMarketplaceAndActiveTrue(
      Long accountId,
      MarketplaceType marketplaceType);

  boolean existsByAccount_IdAndMarketplace(Long accountId, MarketplaceType marketplace);

  boolean existsByAccount_IdAndMarketplaceAndIdNot(
      Long accountId,
      MarketplaceType marketplace,
      Long id);

  Set<AccountConnectionEntity> findAllByAccount_IdAndActiveTrue(Long accountId);

  Optional<AccountConnectionEntity> findByAccount_IdAndMarketplace(
      Long accountId,
      MarketplaceType marketplace);
}
