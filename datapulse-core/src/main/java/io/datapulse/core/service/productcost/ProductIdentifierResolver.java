package io.datapulse.core.service.productcost;

import java.util.Optional;

public interface ProductIdentifierResolver {

  Optional<Long> resolveProductId(Long accountId, String sourceProductId);
}
