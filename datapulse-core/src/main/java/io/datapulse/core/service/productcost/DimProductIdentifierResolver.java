package io.datapulse.core.service.productcost;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.repository.product.DimProductRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
public class DimProductIdentifierResolver implements ProductIdentifierResolver {

  private final DimProductRepository dimProductRepository;

  @Override
  public Optional<Long> resolveProductId(Long accountId, String sourceProductId) {
    return dimProductRepository
        .findByAccountIdAndSourceProductId(accountId, sourceProductId)
        .map(LongBaseEntity::getId);
  }
}
