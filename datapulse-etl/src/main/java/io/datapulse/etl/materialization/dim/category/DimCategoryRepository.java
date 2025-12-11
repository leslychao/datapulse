package io.datapulse.etl.materialization.dim.category;

public interface DimCategoryRepository {

  void upsertOzon(Long accountId, String requestId);

  void upsertWildberries(Long accountId, String requestId);
}
