package io.datapulse.etl.repository;

public interface DimSubjectWbRepository {

  void upsert(long accountId, String requestId);
}
