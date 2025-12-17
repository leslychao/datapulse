package io.datapulse.etl.materialization.sales;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
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
          product_id,
          offer_id,
          warehouse_id,
          category_id,
          quantity,
          gross_amount,
          commission_amount,
          net_amount,
          currency_code,
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
          product_id,
          offer_id,
          warehouse_id,
          category_id,
          quantity,
          gross_amount,
          commission_amount,
          net_amount,
          currency_code,
          sale_date,
          operation_type,
          now(),
          now()
      from (%s) as source
      on conflict (source_platform, source_event_id) do update
        set account_id        = excluded.account_id,
            order_id          = excluded.order_id,
            product_id        = excluded.product_id,
            offer_id          = excluded.offer_id,
            warehouse_id      = excluded.warehouse_id,
            category_id       = excluded.category_id,
            quantity          = excluded.quantity,
            gross_amount      = excluded.gross_amount,
            commission_amount = excluded.commission_amount,
            net_amount        = excluded.net_amount,
            currency_code     = excluded.currency_code,
            sale_date         = excluded.sale_date,
            operation_type    = excluded.operation_type,
            updated_at        = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    Objects.requireNonNull(accountId, "accountId обязателен.");
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        select distinct on (source_event_id)
            account_id,
            source_platform,
            source_event_id,
            order_id,
            product_id,
            offer_id,
            warehouse_id,
            category_id,
            quantity,
            gross_amount,
            commission_amount,
            net_amount,
            currency_code,
            sale_date,
            operation_type
        from (
            select
                r.account_id                                         as account_id,
                '%1$s'                                               as source_platform,
                (r.payload::jsonb ->> 'rrd_id')                      as source_event_id,

                (r.payload::jsonb ->> 'order_uid')                   as order_id,
                (r.payload::jsonb ->> 'nm_id')                       as product_id,
                (r.payload::jsonb ->> 'sa_name')                     as offer_id,

                (r.payload::jsonb ->> 'ppvz_office_id')::bigint      as warehouse_id,

                c.id                                                 as category_id,

                nullif(r.payload::jsonb ->> 'quantity','')::int      as quantity,

                nullif(r.payload::jsonb ->> 'retail_price_withdisc_rub','')::numeric(14,2) as gross_amount,
                nullif(r.payload::jsonb ->> 'ppvz_sales_commission','')::numeric(14,2)     as commission_amount,
                nullif(r.payload::jsonb ->> 'ppvz_for_pay','')::numeric(14,2)              as net_amount,

                'RUB'                                                as currency_code,

                (r.payload::jsonb ->> 'sale_dt')::timestamptz::date  as sale_date,

                (r.payload::jsonb ->> 'doc_type_name')               as operation_type
            from %2$s r
            left join dim_category c
              on c.source_platform = '%1$s'
             and c.category_name = (r.payload::jsonb ->> 'subject_name')
            where r.account_id = ?
              and r.request_id = ?
              and (r.payload::jsonb ->> 'rrd_id') is not null
              and (r.payload::jsonb ->> 'nm_id') is not null
              and nullif(r.payload::jsonb ->> 'quantity','') is not null
              and (r.payload::jsonb ->> 'sale_dt') is not null
              and (r.payload::jsonb ->> 'doc_type_name') in ('Продажа','Возврат')
        ) t
        order by source_event_id, sale_date desc nulls last
        """.formatted(platform, RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

    String sql = UPSERT_TEMPLATE.formatted(selectQuery);
    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertOzonPostingsFbs(Long accountId, String requestId) {
    Objects.requireNonNull(accountId, "accountId обязателен.");
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        select distinct on (source_event_id)
            account_id,
            source_platform,
            source_event_id,
            order_id,
            product_id,
            offer_id,
            warehouse_id,
            category_id,
            quantity,
            gross_amount,
            commission_amount,
            net_amount,
            currency_code,
            sale_date,
            operation_type
        from (
            select
                p.account_id                                                     as account_id,
                '%1$s'                                                           as source_platform,

                (p.payload::jsonb ->> 'posting_number') || ':' || (item ->> 'sku') as source_event_id,

                (p.payload::jsonb ->> 'posting_number')                          as order_id,
                (item ->> 'sku')                                                 as product_id,
                (item ->> 'offer_id')                                            as offer_id,

                (p.payload::jsonb -> 'delivery_method' ->> 'warehouse_id')::bigint as warehouse_id,

                null::bigint                                                     as category_id,

                nullif(item ->> 'quantity','')::int                              as quantity,

                round(((item ->> 'price')::numeric * (item ->> 'quantity')::numeric), 2)::numeric(14,2) as gross_amount,

                null::numeric(14,2)                                              as commission_amount,
                null::numeric(14,2)                                              as net_amount,

                (item ->> 'currency_code')                                       as currency_code,

                (p.payload::jsonb ->> 'shipment_date')::timestamptz::date        as sale_date,

                (p.payload::jsonb ->> 'status')                                  as operation_type
            from %2$s p
            join lateral jsonb_array_elements(p.payload::jsonb -> 'products') item on true
            where p.account_id = ?
              and p.request_id = ?
              and (p.payload::jsonb ->> 'posting_number') is not null
              and (item ->> 'sku') is not null
        ) t
        order by source_event_id, sale_date desc nulls last
        """.formatted(platform, RawTableNames.RAW_OZON_POSTINGS_FBS);

    String sql = UPSERT_TEMPLATE.formatted(selectQuery);
    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertOzonFinanceTransactions(Long accountId, String requestId) {
    Objects.requireNonNull(accountId, "accountId обязателен.");
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    // ВНИМАНИЕ: это финансовый поток, в fact_sales он должен попадать только если ты сознательно так решил.
    // Архитектурно лучше вынести в отдельный fact_finance.
    String selectQuery = """
        select distinct on (source_event_id)
            account_id,
            source_platform,
            source_event_id,
            order_id,
            product_id,
            offer_id,
            warehouse_id,
            category_id,
            quantity,
            gross_amount,
            commission_amount,
            net_amount,
            currency_code,
            sale_date,
            operation_type
        from (
            select
                f.account_id                                              as account_id,
                '%1$s'                                                    as source_platform,

                (f.payload::jsonb ->> 'operation_id')                      as source_event_id,

                (f.payload::jsonb -> 'posting' ->> 'posting_number')       as order_id,

                null::text                                                as product_id,
                null::text                                                as offer_id,

                nullif(f.payload::jsonb -> 'posting' ->> 'warehouse_id','')::bigint as warehouse_id,

                null::bigint                                              as category_id,

                null::int                                                 as quantity,

                nullif(f.payload::jsonb ->> 'amount','')::numeric(14,2)    as gross_amount,
                nullif(f.payload::jsonb ->> 'sale_commission','')::numeric(14,2) as commission_amount,
                nullif(f.payload::jsonb ->> 'accruals_for_sale','')::numeric(14,2) as net_amount,

                'RUB'                                                     as currency_code,

                (f.payload::jsonb ->> 'operation_date')::timestamp::date   as sale_date,

                (f.payload::jsonb ->> 'operation_type')                    as operation_type
            from %2$s f
            where f.account_id = ?
              and f.request_id = ?
              and (f.payload::jsonb ->> 'operation_id') is not null
        ) t
        order by source_event_id, sale_date desc nulls last
        """.formatted(platform, RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

    String sql = UPSERT_TEMPLATE.formatted(selectQuery);
    jdbcTemplate.update(sql, accountId, requestId);
  }
}
