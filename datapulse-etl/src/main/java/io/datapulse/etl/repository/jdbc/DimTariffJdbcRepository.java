package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimTariffRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimTariffJdbcRepository implements DimTariffRepository {

  private static final String UPSERT_WB_SCD2_TEMPLATE = """
      with source as (
          select
              '%1$s'                                                   as source_platform,
              c.id                                                     as dim_category_id,

              (t.payload::jsonb ->> 'kgvpBooking')::numeric(10,4)         as kgvp_booking,
              (t.payload::jsonb ->> 'kgvpMarketplace')::numeric(10,4)     as kgvp_marketplace,
              (t.payload::jsonb ->> 'kgvpPickup')::numeric(10,4)          as kgvp_pickup,
              (t.payload::jsonb ->> 'kgvpSupplier')::numeric(10,4)        as kgvp_supplier,
              (t.payload::jsonb ->> 'kgvpSupplierExpress')::numeric(10,4) as kgvp_supplier_express,
              (t.payload::jsonb ->> 'paidStorageKgvp')::numeric(10,4)     as paid_storage_kgvp
          from %2$s t
          join dim_category c
            on c.source_platform    = '%1$s'
           and c.source_category_id = (t.payload::jsonb ->> 'subjectID')::bigint
          where t.account_id = ?
            and t.request_id = ?
      ),
      closed as (
          update dim_tariff_wb d
          set valid_to  = current_date - 1,
              updated_at = now()
          from source s
          where d.source_platform  = s.source_platform
            and d.dim_category_id  = s.dim_category_id
            and d.valid_to is null
            and (
              (d.kgvp_booking,
               d.kgvp_marketplace,
               d.kgvp_pickup,
               d.kgvp_supplier,
               d.kgvp_supplier_express,
               d.paid_storage_kgvp)
              is distinct from
              (s.kgvp_booking,
               s.kgvp_marketplace,
               s.kgvp_pickup,
               s.kgvp_supplier,
               s.kgvp_supplier_express,
               s.paid_storage_kgvp)
            )
      )
      insert into dim_tariff_wb (
          source_platform,
          dim_category_id,
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
          s.dim_category_id,
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
          from dim_tariff_wb d
          where d.source_platform  = s.source_platform
            and d.dim_category_id  = s.dim_category_id
            and d.valid_to is null
            and (
              (d.kgvp_booking,
               d.kgvp_marketplace,
               d.kgvp_pickup,
               d.kgvp_supplier,
               d.kgvp_supplier_express,
               d.paid_storage_kgvp)
              is not distinct from
              (s.kgvp_booking,
               s.kgvp_marketplace,
               s.kgvp_pickup,
               s.kgvp_supplier,
               s.kgvp_supplier_express,
               s.paid_storage_kgvp)
            )
      );
      """;

  private static final String UPSERT_OZON_SCD2_TEMPLATE = """
      with raw as (
          select
              (t.payload::jsonb ->> 'product_id')::bigint              as product_id,
              t.payload::jsonb ->> 'offer_id'                          as offer_id,
              (t.payload::jsonb ->> 'acquiring')::numeric(10,4)        as acquiring,

              (t.payload::jsonb -> 'commissions' ->> 'sales_percent_fbo')::numeric(10,4)  as sales_percent_fbo,
              (t.payload::jsonb -> 'commissions' ->> 'sales_percent_fbs')::numeric(10,4)  as sales_percent_fbs,
              (t.payload::jsonb -> 'commissions' ->> 'sales_percent_rfbs')::numeric(10,4) as sales_percent_rfbs,
              (t.payload::jsonb -> 'commissions' ->> 'sales_percent_fbp')::numeric(10,4)  as sales_percent_fbp,

              (t.payload::jsonb -> 'commissions' ->> 'fbo_deliv_to_customer_amount')::numeric(10,4)     as fbo_deliv_to_customer_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbo_direct_flow_trans_min_amount')::numeric(10,4) as fbo_direct_flow_trans_min_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbo_direct_flow_trans_max_amount')::numeric(10,4) as fbo_direct_flow_trans_max_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbo_return_flow_amount')::numeric(10,4)           as fbo_return_flow_amount,

              (t.payload::jsonb -> 'commissions' ->> 'fbs_deliv_to_customer_amount')::numeric(10,4)     as fbs_deliv_to_customer_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbs_direct_flow_trans_min_amount')::numeric(10,4) as fbs_direct_flow_trans_min_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbs_direct_flow_trans_max_amount')::numeric(10,4) as fbs_direct_flow_trans_max_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbs_first_mile_min_amount')::numeric(10,4)        as fbs_first_mile_min_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbs_first_mile_max_amount')::numeric(10,4)        as fbs_first_mile_max_amount,
              (t.payload::jsonb -> 'commissions' ->> 'fbs_return_flow_amount')::numeric(10,4)           as fbs_return_flow_amount,

              t.created_at                                            as created_at
          from %1$s t
          where t.account_id = ?
            and t.request_id = ?
            and (t.payload::jsonb ->> 'product_id') is not null
      ),
      source as (
          select distinct on (product_id)
              product_id,
              offer_id,
              acquiring,
              sales_percent_fbo,
              sales_percent_fbs,
              sales_percent_rfbs,
              sales_percent_fbp,
              fbo_deliv_to_customer_amount,
              fbo_direct_flow_trans_min_amount,
              fbo_direct_flow_trans_max_amount,
              fbo_return_flow_amount,
              fbs_deliv_to_customer_amount,
              fbs_direct_flow_trans_min_amount,
              fbs_direct_flow_trans_max_amount,
              fbs_first_mile_min_amount,
              fbs_first_mile_max_amount,
              fbs_return_flow_amount
          from raw
          order by product_id, created_at desc
      ),
      closed as (
          update dim_tariff_ozon d
          set valid_to  = current_date - 1,
              updated_at = now()
          from source s
          where d.product_id = s.product_id
            and d.valid_to is null
            and (
              (d.acquiring,
               d.sales_percent_fbo,
               d.sales_percent_fbs,
               d.sales_percent_rfbs,
               d.sales_percent_fbp,
               d.fbo_deliv_to_customer_amount,
               d.fbo_direct_flow_trans_min_amount,
               d.fbo_direct_flow_trans_max_amount,
               d.fbo_return_flow_amount,
               d.fbs_deliv_to_customer_amount,
               d.fbs_direct_flow_trans_min_amount,
               d.fbs_direct_flow_trans_max_amount,
               d.fbs_first_mile_min_amount,
               d.fbs_first_mile_max_amount,
               d.fbs_return_flow_amount)
              is distinct from
              (s.acquiring,
               s.sales_percent_fbo,
               s.sales_percent_fbs,
               s.sales_percent_rfbs,
               s.sales_percent_fbp,
               s.fbo_deliv_to_customer_amount,
               s.fbo_direct_flow_trans_min_amount,
               s.fbo_direct_flow_trans_max_amount,
               s.fbo_return_flow_amount,
               s.fbs_deliv_to_customer_amount,
               s.fbs_direct_flow_trans_min_amount,
               s.fbs_direct_flow_trans_max_amount,
               s.fbs_first_mile_min_amount,
               s.fbs_first_mile_max_amount,
               s.fbs_return_flow_amount)
            )
      )
      insert into dim_tariff_ozon (
          product_id,
          offer_id,
          acquiring,
          sales_percent_fbo,
          sales_percent_fbs,
          sales_percent_rfbs,
          sales_percent_fbp,
          fbo_deliv_to_customer_amount,
          fbo_direct_flow_trans_min_amount,
          fbo_direct_flow_trans_max_amount,
          fbo_return_flow_amount,
          fbs_deliv_to_customer_amount,
          fbs_direct_flow_trans_min_amount,
          fbs_direct_flow_trans_max_amount,
          fbs_first_mile_min_amount,
          fbs_first_mile_max_amount,
          fbs_return_flow_amount,
          valid_from,
          valid_to,
          created_at,
          updated_at
      )
      select
          s.product_id,
          s.offer_id,
          s.acquiring,
          s.sales_percent_fbo,
          s.sales_percent_fbs,
          s.sales_percent_rfbs,
          s.sales_percent_fbp,
          s.fbo_deliv_to_customer_amount,
          s.fbo_direct_flow_trans_min_amount,
          s.fbo_direct_flow_trans_max_amount,
          s.fbo_return_flow_amount,
          s.fbs_deliv_to_customer_amount,
          s.fbs_direct_flow_trans_min_amount,
          s.fbs_direct_flow_trans_max_amount,
          s.fbs_first_mile_min_amount,
          s.fbs_first_mile_max_amount,
          s.fbs_return_flow_amount,
          current_date as valid_from,
          null::date   as valid_to,
          now(),
          now()
      from source s
      where not exists (
          select 1
          from dim_tariff_ozon d
          where d.product_id = s.product_id
            and d.valid_to is null
            and (
              (d.acquiring,
               d.sales_percent_fbo,
               d.sales_percent_fbs,
               d.sales_percent_rfbs,
               d.sales_percent_fbp,
               d.fbo_deliv_to_customer_amount,
               d.fbo_direct_flow_trans_min_amount,
               d.fbo_direct_flow_trans_max_amount,
               d.fbo_return_flow_amount,
               d.fbs_deliv_to_customer_amount,
               d.fbs_direct_flow_trans_min_amount,
               d.fbs_direct_flow_trans_max_amount,
               d.fbs_first_mile_min_amount,
               d.fbs_first_mile_max_amount,
               d.fbs_return_flow_amount)
              is not distinct from
              (s.acquiring,
               s.sales_percent_fbo,
               s.sales_percent_fbs,
               s.sales_percent_rfbs,
               s.sales_percent_fbp,
               s.fbo_deliv_to_customer_amount,
               s.fbo_direct_flow_trans_min_amount,
               s.fbo_direct_flow_trans_max_amount,
               s.fbo_return_flow_amount,
               s.fbs_deliv_to_customer_amount,
               s.fbs_direct_flow_trans_min_amount,
               s.fbs_direct_flow_trans_max_amount,
               s.fbs_first_mile_min_amount,
               s.fbs_first_mile_max_amount,
               s.fbs_return_flow_amount)
            )
      );
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    Objects.requireNonNull(accountId, "accountId обязателен.");
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String sql = UPSERT_WB_SCD2_TEMPLATE.formatted(
        platform,
        RawTableNames.RAW_WB_TARIFFS_COMMISSION
    );

    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    Objects.requireNonNull(accountId, "accountId обязателен.");
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String sql = UPSERT_OZON_SCD2_TEMPLATE.formatted(
        RawTableNames.RAW_OZON_PRODUCT_INFO_PRICES
    );

    jdbcTemplate.update(sql, accountId, requestId);
  }
}
