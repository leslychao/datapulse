package io.datapulse.core.service.productcost;

public interface ProductIdentifierResolver {

  Long resolveProductId(Long accountId, String sourceProductId);
}
