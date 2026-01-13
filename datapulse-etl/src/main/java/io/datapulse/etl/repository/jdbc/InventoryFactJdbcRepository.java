package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.InventoryFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryFactJdbcRepository implements InventoryFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_inventory_snapshot (
          account_id,
          source_platform,
          snapshot_date,
          source_product_id,
          warehouse_id,
          quantity_total,
          quantity_available,
          quantity_reserved,
          quantity_in_way_to_client,
          quantity_in_way_from_client,
          quantity_return_to_seller,
          quantity_return_from_customer,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          snapshot_date,
          source_product_id,
          warehouse_id,
          quantity_total,
          quantity_available,
          quantity_reserved,
          quantity_in_way_to_client,
          quantity_in_way_from_client,
          quantity_return_to_seller,
          quantity_return_from_customer,
          now(),
          now()
      from (%s) as source
      on conflict (account_id, source_platform, snapshot_date, source_product_id, warehouse_id)
        do update
        set quantity_total                = excluded.quantity_total,
            quantity_available            = excluded.quantity_available,
            quantity_reserved             = excluded.quantity_reserved,
            quantity_in_way_to_client     = excluded.quantity_in_way_to_client,
            quantity_in_way_from_client   = excluded.quantity_in_way_from_client,
            quantity_return_to_seller     = excluded.quantity_return_to_seller,
            quantity_return_from_customer = excluded.quantity_return_from_customer,
            updated_at                    = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzonAnalyticsStocks(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        select distinct on (account_id, source_platform, snapshot_date, source_product_id, warehouse_id)
            account_id,
            source_platform,
            snapshot_date,
            source_product_id,
            warehouse_id,
            quantity_total,
            quantity_available,
            quantity_reserved,
            quantity_in_way_to_client,
            quantity_in_way_from_client,
            quantity_return_to_seller,
            quantity_return_from_customer
        from (
            select
                r.account_id                                                         as account_id,
                '%1$s'                                                               as source_platform,
                r.created_at::date                                                   as snapshot_date,

                nullif(r.payload::jsonb ->> 'sku','')                                as source_product_id,

                w.id                                                                 as warehouse_id,

                (
                  coalesce(
                      nullif(r.payload::jsonb ->> 'available_stock_count','')::int,
                      0
                  )
                  + coalesce(
                      nullif(r.payload::jsonb ->> 'requested_stock_count','')::int,
                      0
                  )
                )                                                                    as quantity_total,

                coalesce(
                    nullif(r.payload::jsonb ->> 'available_stock_count','')::int,
                    0
                )                                                                    as quantity_available,

                coalesce(
                    nullif(r.payload::jsonb ->> 'requested_stock_count','')::int,
                    0
                )                                                                    as quantity_reserved,

                0                                                                    as quantity_in_way_to_client,
                0                                                                    as quantity_in_way_from_client,

                coalesce(
                    nullif(r.payload::jsonb ->> 'return_to_seller_stock_count','')::int,
                    0
                )                                                                    as quantity_return_to_seller,

                coalesce(
                    nullif(r.payload::jsonb ->> 'return_from_customer_stock_count','')::int,
                    0
                )                                                                    as quantity_return_from_customer,

                r.created_at                                                         as created_at
            from %2$s r
            left join dim_warehouse w
              on w.account_id = r.account_id
             and lower(w.source_platform) = lower('%1$s')
             and w.external_warehouse_id = nullif(r.payload::jsonb ->> 'warehouse_id','')

            where r.account_id = ?
              and r.request_id = ?
              and r.marketplace = '%3$s'
              and nullif(r.payload::jsonb ->> 'sku','') is not null
              and w.id is not null
        ) t
        order by account_id, source_platform, snapshot_date, source_product_id, warehouse_id,
                 created_at desc nulls last
        """.formatted(
        platform,
        RawTableNames.RAW_OZON_ANALYTICS_STOCKS,
        MarketplaceType.OZON.name()
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertOzonProductInfoStocks(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        select distinct on (account_id, source_platform, snapshot_date, source_product_id, warehouse_id)
            account_id,
            source_platform,
            snapshot_date,
            source_product_id,
            warehouse_id,
            quantity_total,
            quantity_available,
            quantity_reserved,
            quantity_in_way_to_client,
            quantity_in_way_from_client,
            quantity_return_to_seller,
            quantity_return_from_customer
        from (
            select
                r.account_id                                                         as account_id,
                '%1$s'                                                               as source_platform,
                r.created_at::date                                                   as snapshot_date,

                nullif(stock ->> 'sku','')                                           as source_product_id,

                w.id                                                                 as warehouse_id,

                coalesce(
                    nullif(stock ->> 'present','')::int,
                    0
                )                                                                    as quantity_total,

                greatest(
                    coalesce(nullif(stock ->> 'present','')::int, 0)
                    - coalesce(nullif(stock ->> 'reserved','')::int, 0),
                    0
                )                                                                    as quantity_available,

                coalesce(
                    nullif(stock ->> 'reserved','')::int,
                    0
                )                                                                    as quantity_reserved,

                0                                                                    as quantity_in_way_to_client,
                0                                                                    as quantity_in_way_from_client,
                0                                                                    as quantity_return_to_seller,
                0                                                                    as quantity_return_from_customer,

                r.created_at                                                         as created_at
            from %2$s r
            join lateral jsonb_array_elements(r.payload::jsonb -> 'stocks') stock on true
            join lateral jsonb_array_elements_text(stock -> 'warehouse_ids') wh_id on true

            left join dim_warehouse w
              on w.account_id = r.account_id
             and lower(w.source_platform) = lower('%1$s')
             and w.external_warehouse_id = nullif(wh_id, '')

            where r.account_id = ?
              and r.request_id = ?
              and r.marketplace = '%3$s'
              and nullif(stock ->> 'sku','')      is not null
              and w.id is not null
        ) t
        order by account_id, source_platform, snapshot_date, source_product_id, warehouse_id,
                 created_at desc nulls last
        """.formatted(
        platform,
        RawTableNames.RAW_OZON_PRODUCT_INFO_STOCKS,
        MarketplaceType.OZON.name()
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertWbStocks(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        select distinct on (account_id, source_platform, snapshot_date, source_product_id, warehouse_id)
            account_id,
            source_platform,
            snapshot_date,
            source_product_id,
            warehouse_id,
            quantity_total,
            quantity_available,
            quantity_reserved,
            quantity_in_way_to_client,
            quantity_in_way_from_client,
            quantity_return_to_seller,
            quantity_return_from_customer
        from (
            select
                r.account_id                                                         as account_id,
                '%1$s'                                                               as source_platform,

                coalesce(
                    (r.payload::jsonb ->> 'lastChangeDate')::timestamptz::date,
                    r.created_at::date
                )                                                                    as snapshot_date,

                nullif(r.payload::jsonb ->> 'nmId','')                               as source_product_id,

                w.id                                                                 as warehouse_id,

                coalesce(
                    nullif(r.payload::jsonb ->> 'quantity','')::int,
                    0
                )                                                                    as quantity_total,

                coalesce(
                    nullif(r.payload::jsonb ->> 'quantity','')::int,
                    0
                )                                                                    as quantity_available,

                0                                                                    as quantity_reserved,

                coalesce(
                    nullif(r.payload::jsonb ->> 'inWayToClient','')::int,
                    0
                )                                                                    as quantity_in_way_to_client,

                coalesce(
                    nullif(r.payload::jsonb ->> 'inWayFromClient','')::int,
                    0
                )                                                                    as quantity_in_way_from_client,

                0                                                                    as quantity_return_to_seller,
                0                                                                    as quantity_return_from_customer,

                r.created_at                                                         as created_at
            from %2$s r

            left join dim_warehouse w
              on w.account_id = r.account_id
             and lower(w.source_platform) = lower('%1$s')
             and (
                  w.external_warehouse_id = nullif(r.payload::jsonb ->> 'SCCode','')
                  or lower(trim(w.warehouse_name)) = lower(trim(nullif(r.payload::jsonb ->> 'warehouseName','')))
                 )

            where r.account_id = ?
              and r.request_id = ?
              and r.marketplace = '%3$s'
              and nullif(r.payload::jsonb ->> 'nmId','') is not null
              and w.id is not null
        ) t
        order by account_id, source_platform, snapshot_date, source_product_id, warehouse_id,
                 created_at desc nulls last
        """.formatted(
        platform,
        RawTableNames.RAW_WB_STOCKS,
        MarketplaceType.WILDBERRIES.name()
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
