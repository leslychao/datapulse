package io.datapulse.core.service.productcost;

import static io.datapulse.domain.MessageCodes.PRODUCT_COST_PRODUCT_BY_SOURCE_ID_NOT_FOUND;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.repository.product.DimProductRepository;
import io.datapulse.domain.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
public class DimProductIdentifierResolver implements ProductIdentifierResolver {

  private final DimProductRepository dimProductRepository;

  @Override
  public Long resolveProductId(Long accountId, String sourceProductId) {
    return dimProductRepository
        .findByAccountIdAndSourceProductId(accountId, sourceProductId)
        .map(LongBaseEntity::getId)
        .orElseThrow(() -> new BadRequestException(
            PRODUCT_COST_PRODUCT_BY_SOURCE_ID_NOT_FOUND,
            accountId,
            sourceProductId
        ));
  }
}
