package io.datapulse.core.repository.product;

import io.datapulse.core.entity.product.DimProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DimProductRepository extends JpaRepository<DimProductEntity, Long> {

  Optional<DimProductEntity> findByAccountIdAndSourceProductId(
      Long accountId,
      String sourceProductId
  );
}
