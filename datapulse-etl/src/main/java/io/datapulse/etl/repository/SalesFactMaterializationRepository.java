package io.datapulse.etl.repository;

import io.datapulse.core.entity.SalesFactEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SalesFactMaterializationRepository extends Repository<SalesFactEntity, Long> {

  @Modifying
  @Query(
      value = "call materialize_sales_fact(:accountId, :dateFrom, :dateTo)",
      nativeQuery = true
  )
  void materializeSalesFact(
      @Param("accountId") long accountId,
      @Param("dateFrom") LocalDate dateFrom,
      @Param("dateTo") LocalDate dateTo
  );
}
