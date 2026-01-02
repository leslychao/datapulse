package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.ReturnsFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReturnsFactJdbcRepository implements ReturnsFactRepository {

  private static final String UPSERT_RETURNS_TEMPLATE = """
      insert into fact_returns (
          account_id,
          source_platform,
          source_event_id,
          dim_product_id,
          order_id,
          warehouse_id,
          category_id,
          quantity,
          return_date,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          dim_product_id,
          order_id,
          warehouse_id,
          category_id,
          quantity,
          return_date,
          now(),
          now()
      from (%s) as source
      on conflict (account_id, source_platform, source_event_id) do update
        set dim_product_id = excluded.dim_product_id,
            order_id       = excluded.order_id,
            warehouse_id   = excluded.warehouse_id,
            category_id    = excluded.category_id,
            quantity       = excluded.quantity,
            return_date    = excluded.return_date,
            updated_at     = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        select distinct on (source_event_id)
            account_id,
            source_platform,
            source_event_id,
            dim_product_id,
            order_id,
            warehouse_id,
            category_id,
            quantity,
            return_date
        from (
            select
                account_id,
                source_platform,
                source_event_id,
                dim_product_id,
                order_id,
                warehouse_id,
                category_id,
                sum(quantity)   as quantity,
                return_date,
                max(created_at) as created_at
            from (
                select
                    fs.account_id                                                    as account_id,
                    '%1$s'                                                           as source_platform,

                    nullif(r.payload::jsonb ->> 'saleID','')                         as source_event_id,

                    fs.dim_product_id                                                as dim_product_id,
                    fs.order_id                                                      as order_id,
                    fs.warehouse_id                                                 as warehouse_id,
                    fs.category_id                                                  as category_id,

                    1                                                                as quantity,

                    (r.payload::jsonb ->> 'date')::timestamptz::date                 as return_date,

                    r.created_at                                                     as created_at
                from %2$s r

                join fact_sales fs
                  on fs.account_id      = r.account_id
                 and fs.source_platform = '%1$s'
                 and fs.source_event_id = nullif(r.payload::jsonb ->> 'saleID','')

                where r.account_id = ?
                  and r.request_id = ?
                  and nullif(r.payload::jsonb ->> 'saleID','') is not null
                  and nullif(r.payload::jsonb ->> 'nmId','')   is not null
                  and nullif(r.payload::jsonb ->> 'date','')   is not null
                  and nullif(r.payload::jsonb ->> 'forPay','') is not null
                  and (r.payload::jsonb ->> 'forPay')::numeric < 0
            ) s
            group by
                account_id,
                source_platform,
                source_event_id,
                dim_product_id,
                order_id,
                warehouse_id,
                category_id,
                return_date
        ) t
        where quantity > 0
        order by source_event_id, created_at desc nulls last
        """.formatted(platform, RawTableNames.RAW_WB_SUPPLIER_SALES);

    jdbcTemplate.update(UPSERT_RETURNS_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertOzonReturns(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        select distinct on (source_event_id)
            account_id,
            source_platform,
            source_event_id,
            dim_product_id,
            order_id,
            warehouse_id,
            category_id,
            quantity,
            return_date
        from (
            select
                fs.account_id                                                     as account_id,
                '%1$s'                                                            as source_platform,

                nullif(r.payload::jsonb ->> 'id','')                              as source_event_id,

                fs.dim_product_id                                                 as dim_product_id,
                fs.order_id                                                       as order_id,
                fs.warehouse_id                                                  as warehouse_id,
                fs.category_id                                                   as category_id,

                coalesce(
                    nullif(r.payload::jsonb -> 'product' ->> 'quantity','')::int,
                    1
                )                                                                 as quantity,

                (r.payload::jsonb -> 'logistic' ->> 'return_date')::timestamptz::date
                                                                                  as return_date,

                r.created_at                                                      as created_at
            from %2$s r

            join fact_sales fs
              on fs.account_id      = r.account_id
             and fs.source_platform = '%1$s'
             and fs.source_event_id =
                 nullif(r.payload::jsonb ->> 'posting_number','') || ':' ||
                 nullif(r.payload::jsonb -> 'product' ->> 'sku','')

            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'id','') is not null
              and nullif(r.payload::jsonb -> 'product' ->> 'sku','') is not null
              and nullif(r.payload::jsonb -> 'logistic' ->> 'return_date','') is not null
        ) t
        order by source_event_id, created_at desc nulls last
        """.formatted(platform, RawTableNames.RAW_OZON_RETURNS);

    jdbcTemplate.update(UPSERT_RETURNS_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
