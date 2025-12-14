package io.datapulse.etl.materialization.dim.tariff;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimTariffJdbcRepository implements DimTariffRepository {

  private static final String INSERT_TEMPLATE = """
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
          now(),
          now()
      from (%s) as source
      on conflict (source_platform, subject_id, valid_from) do update
        set parent_id             = excluded.parent_id,
            parent_name           = excluded.parent_name,
            subject_name          = excluded.subject_name,
            kgvp_booking          = excluded.kgvp_booking,
            kgvp_marketplace      = excluded.kgvp_marketplace,
            kgvp_pickup           = excluded.kgvp_pickup,
            kgvp_supplier         = excluded.kgvp_supplier,
            kgvp_supplier_express = excluded.kgvp_supplier_express,
            paid_storage_kgvp     = excluded.paid_storage_kgvp,
            valid_to              = excluded.valid_to,
            updated_at            = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
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
            (t.payload::jsonb ->> 'paidStorageKgvp')::numeric      as paid_storage_kgvp,

            current_date as valid_from,
            null::date   as valid_to
        from %2$s t
        where t.account_id = ? and t.request_id = ?
        """.formatted(platform, RawTableNames.RAW_WB_TARIFFS_COMMISSION);

    String sql = INSERT_TEMPLATE.formatted(selectQuery);
    jdbcTemplate.update(sql, accountId, requestId);
  }
}
