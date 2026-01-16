package io.datapulse.core.repository.account;

import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.domain.MarketplaceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountConnectionRepository extends JpaRepository<AccountConnectionEntity, Long> {

  List<AccountConnectionEntity> findAllByAccount_Id(Long accountId);

  List<AccountConnectionEntity> findAllByAccount_IdAndActiveTrue(Long accountId);

  Optional<AccountConnectionEntity> findByAccount_IdAndMarketplace(Long accountId,
      MarketplaceType marketplace);

  Optional<AccountConnectionEntity> findByIdAndAccount_Id(Long id, Long accountId);

  boolean existsByAccount_IdAndMarketplace(Long accountId, MarketplaceType marketplace);

  boolean existsByAccount_IdAndMarketplaceAndActiveTrue(Long accountId,
      MarketplaceType marketplaceType);
}
