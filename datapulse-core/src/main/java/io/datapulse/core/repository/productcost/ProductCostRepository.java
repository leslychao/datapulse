package io.datapulse.core.repository.productcost;

import io.datapulse.core.entity.productcost.ProductCostEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCostRepository extends JpaRepository<ProductCostEntity, Long> {

  Optional<ProductCostEntity> findByAccountIdAndProductIdAndValidFrom(
      Long accountId,
      Long productId,
      LocalDate validFrom
  );
}
