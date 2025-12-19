package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimTariffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimTariffJdbcRepository implements DimTariffRepository {

  private static final String UPSERT_WB_SCD2_TEMPLATE = """
      with source as (
          select
              '%1$s' as source_platform,

              (t.payload::jsonb ->> 'parentID')::bigint   as parent_id,
              (t.payload::jsonb ->> 'parentName')::text   as parent_name,

              (t.payload::jsonb ->> 'subjectID')::bigint  as subject_id,
              (t.payload::jsonb ->> 'subjectName')::text  as subject_name,

              (t.payload::jsonb ->> 'kgvpBooking')::numeric          as kgvp_booking,
              (t.payload::jsonb ->> 'kgvpMarketplace')::numeric      as kgvp_marketplace,
              (t.payload::jsonb ->> 'kgvpPickup')::numeric           as kgvp_pickup,
              (t.payload::jsonb ->> 'kgvpSupplier')::numeric         as kgvp_supplier,
              (t.payload::jsonb ->> 'kgvpSupplierExpress')::numeric  as kgvp_supplier_express,
              (t.payload::jsonb ->> 'paidStorageKgvp')::numeric      as paid_storage_kgvp
          from %2$s t
          where t.account_id = ?
            and t.request_id = ?
      ),
      closed as (
          update dim_tariff d
          set valid_to = current_date - 1,
              updated_at = now()
          from source s
          where d.source_platform = s.source_platform
            and d.subject_id      = s.subject_id
            and d.valid_to is null
            and (
              (d.parent_id,
               d.parent_name,
               d.subject_name,
               d.kgvp_booking,
               d.kgvp_marketplace,
               d.kgvp_pickup,
               d.kgvp_supplier,
               d.kgvp_supplier_express,
               d.paid_storage_kgvp)
              is distinct from
              (s.parent_id,
               s.parent_name,
               s.subject_name,
               s.kgvp_booking,
               s.kgvp_marketplace,
               s.kgvp_pickup,
               s.kgvp_supplier,
               s.kgvp_supplier_express,
               s.paid_storage_kgvp)
            )
      )
      insert into dim_tariff (
          source_platform,
          parent_id,
          parent_name,
          subject_id,
          subject_name,
          kgvp_booking,
          kgvp_marketplace,
          kgvp_pickup,
          kgvp_supplier,
          kgvp_supplier_express,
          paid_storage_kgvp,
          valid_from,
          valid_to,
          created_at,
          updated_at
      )
      select
          s.source_platform,
          s.parent_id,
          s.parent_name,
          s.subject_id,
          s.subject_name,
          s.kgvp_booking,
          s.kgvp_marketplace,
          s.kgvp_pickup,
          s.kgvp_supplier,
          s.kgvp_supplier_express,
          s.paid_storage_kgvp,
          current_date as valid_from,
          null::date   as valid_to,
          now(),
          now()
      from source s
      where not exists (
          select 1
          from dim_tariff d
          where d.source_platform = s.source_platform
            and d.subject_id      = s.subject_id
            and d.valid_to is null
            and (
              (d.parent_id,
               d.parent_name,
               d.subject_name,
               d.kgvp_booking,
               d.kgvp_marketplace,
               d.kgvp_pickup,
               d.kgvp_supplier,
               d.kgvp_supplier_express,
               d.paid_storage_kgvp)
              is not distinct from
              (s.parent_id,
               s.parent_name,
               s.subject_name,
               s.kgvp_booking,
               s.kgvp_marketplace,
               s.kgvp_pickup,
               s.kgvp_supplier,
               s.kgvp_supplier_express,
               s.paid_storage_kgvp)
            )
      );
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    String platform = MarketplaceType.WILDBERRIES.tag();

    String sql = UPSERT_WB_SCD2_TEMPLATE.formatted(
        platform,
        RawTableNames.RAW_WB_TARIFFS_COMMISSION
    );

    jdbcTemplate.update(sql, accountId, requestId);
  }
}
