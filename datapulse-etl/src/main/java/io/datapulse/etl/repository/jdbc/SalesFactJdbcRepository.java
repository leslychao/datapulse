package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.SalesFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
          source_product_id,
          offer_id,
          warehouse_id,
          category_id,
          quantity,
          sale_date,
          operation_type,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          order_id,
          source_product_id,
          offer_id,
          warehouse_id,
          category_id,
          quantity,
          sale_date,
          operation_type,
          now(),
          now()
      from (%s) as source
      on conflict (account_id, source_platform, source_event_id) do update
        set order_id          = excluded.order_id,
            source_product_id = excluded.source_product_id,
            offer_id          = excluded.offer_id,
            warehouse_id      = excluded.warehouse_id,
            category_id       = excluded.category_id,
            quantity          = excluded.quantity,
            sale_date         = excluded.sale_date,
            operation_type    = excluded.operation_type,
            updated_at        = now();
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
            order_id,
            source_product_id,
            offer_id,
            warehouse_id,
            category_id,
            quantity,
            sale_date,
            operation_type
        from (
            select
                r.account_id                                           as account_id,
                '%1$s'                                                 as source_platform,

                nullif(r.payload::jsonb ->> 'saleID','')               as source_event_id,

                nullif(r.payload::jsonb ->> 'srid','')                 as order_id,

                (r.payload::jsonb ->> 'nmId')                          as source_product_id,
                nullif(r.payload::jsonb ->> 'supplierArticle','')      as offer_id,

                w.warehouse_id                                         as warehouse_id,

                dp.category_id                                         as category_id,

                1                                                      as quantity,

                (r.payload::jsonb ->> 'date')::timestamptz::date        as sale_date,

                case
                  when coalesce((r.payload::jsonb ->> 'isRealization')::boolean, true) then 'SALE'
                  else 'RETURN'
                end                                                    as operation_type,

                r.created_at                                           as created_at
            from %2$s r

            left join dim_warehouse_wb w
              on w.name = (r.payload::jsonb ->> 'warehouseName')

            left join dim_product dp
              on dp.account_id = r.account_id
             and dp.source_platform = '%1$s'
             and dp.source_product_id = (r.payload::jsonb ->> 'nmId')

            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'saleID','') is not null
              and nullif(r.payload::jsonb ->> 'nmId','') is not null
              and nullif(r.payload::jsonb ->> 'date','') is not null
        ) t
        order by source_event_id, created_at desc nulls last
        """.formatted(platform, RawTableNames.RAW_WB_SUPPLIER_SALES);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertOzonPostingsFbs(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
      select distinct on (source_event_id)
          account_id,
          source_platform,
          source_event_id,
          order_id,
          source_product_id,
          offer_id,
          warehouse_id,
          category_id,
          quantity,
          sale_date,
          operation_type
      from (
          select
              p.account_id                                                      as account_id,
              '%1$s'                                                            as source_platform,

              (p.payload::jsonb ->> 'posting_number') || ':' || (item ->> 'sku') as source_event_id,

              nullif(p.payload::jsonb ->> 'posting_number','')                   as order_id,

              nullif(item ->> 'sku','')                                          as source_product_id,
              nullif(item ->> 'offer_id','')                                     as offer_id,

              null::bigint                                                       as warehouse_id,

              dp.category_id                                                     as category_id,

              nullif(item ->> 'quantity','')::int                                as quantity,

              (p.payload::jsonb ->> 'shipment_date')::timestamptz::date           as sale_date,

              'SALE'                                                             as operation_type,

              p.created_at                                                       as created_at
          from %2$s p
          join lateral jsonb_array_elements(p.payload::jsonb -> 'products') item on true

          left join dim_product dp
            on dp.account_id = p.account_id
           and dp.source_platform = '%1$s'
           and dp.offer_id = nullif(item ->> 'offer_id','')

          where p.account_id = ?
            and p.request_id = ?
            and nullif(p.payload::jsonb ->> 'posting_number','') is not null
            and nullif(item ->> 'sku','') is not null
            and nullif(item ->> 'quantity','') is not null
            and nullif(p.payload::jsonb ->> 'shipment_date','') is not null
      ) t
      order by source_event_id, created_at desc nulls last
      """.formatted(platform, RawTableNames.RAW_OZON_POSTINGS_FBS);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
