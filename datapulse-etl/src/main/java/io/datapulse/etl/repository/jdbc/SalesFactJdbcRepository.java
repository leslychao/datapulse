package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.repository.SalesFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SalesFactJdbcRepository implements SalesFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_sales (
          account_id,
          source_platform,
          source_event_id,
          order_id,
          dim_product_id,
          warehouse_id,
          category_id,
          quantity,
          sale_ts,
          sale_date,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          order_id,
          dim_product_id,
          warehouse_id,
          category_id,
          quantity,
          sale_ts,
          sale_date,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, source_event_id)
      do update
      set
          order_id      = excluded.order_id,
          dim_product_id = excluded.dim_product_id,
          warehouse_id   = excluded.warehouse_id,
          category_id    = excluded.category_id,
          quantity       = excluded.quantity,
          sale_ts        = excluded.sale_ts,
          sale_date      = excluded.sale_date,
          updated_at     = now();
      """;

  private static final String WB_SOURCE_SELECT = """
      select
          :accountId                                                               as account_id,
          :platform                                                                as source_platform,
          (r.payload::jsonb ->> 'srid') || ':' || (r.payload::jsonb ->> 'nmId')    as source_event_id,
          (r.payload::jsonb ->> 'srid')                                           as order_id,
          dp.id                                                                    as dim_product_id,
          dw.id                                                                    as warehouse_id,
          dc.id                                                                    as category_id,
          count(*)                                                                 as quantity,
          min((r.payload::jsonb ->> 'date')::timestamptz)                         as sale_ts,
          min((r.payload::jsonb ->> 'date')::timestamptz)::date                   as sale_date
      from raw_wb_supplier_sales r
      join dim_product dp
        on dp.account_id        = r.account_id
       and dp.source_platform   = :platform
       and dp.source_product_id = (r.payload::jsonb ->> 'nmId')
      left join dim_warehouse dw
        on dw.account_id      = r.account_id
       and dw.source_platform = :platform
       and dw.warehouse_name  = (r.payload::jsonb ->> 'warehouseName')
      left join dim_category dc
        on dc.source_platform    = :platform
       and dc.source_category_id = dp.external_category_id
      where r.account_id = :accountId
        and r.request_id = :requestId
        and coalesce((r.payload::jsonb ->> 'isRealization')::boolean, false)
        and (r.payload::jsonb ->> 'saleID') like 'S%%'
      group by
        (r.payload::jsonb ->> 'srid'),
        (r.payload::jsonb ->> 'nmId'),
        dp.id,
        dw.id,
        dc.id
      """;

  private static final String OZON_FBS_SOURCE_SELECT = """
      select distinct
          :accountId                                                   as account_id,
          :platform                                                    as source_platform,
          (r.payload::jsonb ->> 'posting_number') || ':' ||
          (p.value ->> 'sku')                                          as source_event_id,
          (r.payload::jsonb ->> 'posting_number')                      as order_id,
          dp.id                                                        as dim_product_id,
          dw.id                                                        as warehouse_id,
          dc.id                                                        as category_id,
          (p.value ->> 'quantity')::int                                as quantity,
          (r.payload::jsonb ->> 'in_process_at')::timestamptz          as sale_ts,
          (r.payload::jsonb ->> 'in_process_at')::timestamptz::date    as sale_date
      from raw_ozon_postings_fbs r
      cross join lateral jsonb_array_elements(r.payload::jsonb -> 'products') as p(value)
      join dim_product dp
        on dp.account_id        = r.account_id
       and dp.source_platform   = :platform
       and dp.source_product_id = (p.value ->> 'sku')
      left join dim_warehouse dw
        on dw.account_id            = r.account_id
       and dw.source_platform       = :platform
       and dw.external_warehouse_id = (r.payload::jsonb #>> '{delivery_method,warehouse_id}')
      left join dim_category dc
        on dc.source_platform    = :platform
       and dc.source_category_id = dp.external_category_id
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.payload::jsonb ->> 'status' = 'delivered'
      """;

  private static final String OZON_FBO_SOURCE_SELECT = """
      select distinct
          :accountId                                                   as account_id,
          :platform                                                    as source_platform,
          (r.payload::jsonb ->> 'posting_number') || ':' ||
          (p.value ->> 'sku')                                          as source_event_id,
          (r.payload::jsonb ->> 'posting_number')                      as order_id,
          dp.id                                                        as dim_product_id,
          dw.id                                                        as warehouse_id,
          dc.id                                                        as category_id,
          (p.value ->> 'quantity')::int                                as quantity,
          (r.payload::jsonb ->> 'in_process_at')::timestamptz          as sale_ts,
          (r.payload::jsonb ->> 'in_process_at')::timestamptz::date    as sale_date
      from raw_ozon_postings_fbo r
      cross join lateral jsonb_array_elements(r.payload::jsonb -> 'products') as p(value)
      join dim_product dp
        on dp.account_id        = r.account_id
       and dp.source_platform   = :platform
       and dp.source_product_id = (p.value ->> 'sku')
      left join dim_warehouse dw
        on dw.account_id            = r.account_id
       and dw.source_platform       = :platform
       and dw.external_warehouse_id = (r.payload::jsonb #>> '{analytics_data,warehouse_id}')
      left join dim_category dc
        on dc.source_platform    = :platform
       and dc.source_category_id = dp.external_category_id
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.payload::jsonb ->> 'status' = 'delivered'
      """;

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String platform = MarketplaceType.WILDBERRIES.tag();
    String query = UPSERT_TEMPLATE.formatted(WB_SOURCE_SELECT);
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("platform", platform);
    namedParameterJdbcTemplate.update(query, params);
  }

  @Override
  public void upsertOzonPostingsFbs(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String platform = MarketplaceType.OZON.tag();
    String query = UPSERT_TEMPLATE.formatted(OZON_FBS_SOURCE_SELECT);
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("platform", platform);
    namedParameterJdbcTemplate.update(query, params);
  }

  @Override
  public void upsertOzonPostingsFbo(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String platform = MarketplaceType.OZON.tag();
    String query = UPSERT_TEMPLATE.formatted(OZON_FBO_SOURCE_SELECT);
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("platform", platform);
    namedParameterJdbcTemplate.update(query, params);
  }
}
