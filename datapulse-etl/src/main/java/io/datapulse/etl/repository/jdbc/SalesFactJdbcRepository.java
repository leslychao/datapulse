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
  public void upsertWildberries(long accountId, String requestId) {
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
                r.account_id                                                as account_id,
                '%1$s'                                                      as source_platform,

                (r.payload::jsonb ->> 'saleID')                              as source_event_id,

                (r.payload::jsonb ->> 'srid')                                as order_id,
                (r.payload::jsonb ->> 'nmId')                                as product_id,
                (r.payload::jsonb ->> 'supplierArticle')                     as offer_id,

                w.warehouse_id                                               as warehouse_id,

                c.id                                                        as category_id,

                1                                                           as quantity,

                nullif(r.payload::jsonb ->> 'priceWithDisc','')::numeric(14,2) as gross_amount,

                case
                  when nullif(r.payload::jsonb ->> 'priceWithDisc','') is null then null::numeric(14,2)
                  when nullif(r.payload::jsonb ->> 'forPay','') is null then null::numeric(14,2)
                  else (
                    nullif(r.payload::jsonb ->> 'priceWithDisc','')::numeric(14,2)
                    - nullif(r.payload::jsonb ->> 'forPay','')::numeric(14,2)
                  )::numeric(14,2)
                end                                                         as commission_amount,

                nullif(r.payload::jsonb ->> 'forPay','')::numeric(14,2)      as net_amount,

                'RUB'                                                       as currency_code,

                (r.payload::jsonb ->> 'date')::timestamptz::date             as sale_date,

                case
                  when coalesce((r.payload::jsonb ->> 'isRealization')::boolean, true) then 'SALE'
                  else 'RETURN'
                end                                                         as operation_type
            from %2$s r

            left join dim_warehouse_wb w
              on w.name = (r.payload::jsonb ->> 'warehouseName')

            left join dim_category c
              on c.source_platform = '%1$s'
             and c.category_name = coalesce(
                  nullif(r.payload::jsonb ->> 'category',''),
                  nullif(r.payload::jsonb ->> 'subject','')
             )

            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'saleID','') is not null
              and nullif(r.payload::jsonb ->> 'nmId','') is not null
              and nullif(r.payload::jsonb ->> 'date','') is not null
        ) t
        order by source_event_id, sale_date desc nulls last
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
              p.account_id                                                      as account_id,
              '%1$s'                                                            as source_platform,

              (p.payload::jsonb ->> 'posting_number') || ':' || (item ->> 'sku') as source_event_id,

              (p.payload::jsonb ->> 'posting_number')                            as order_id,
              (item ->> 'sku')                                                   as product_id,
              (item ->> 'offer_id')                                              as offer_id,

              null::bigint                                                       as warehouse_id,
              null::bigint                                                       as category_id,

              nullif(item ->> 'quantity','')::int                                as quantity,

              coalesce(
                nullif(fin.payload::jsonb ->> 'accruals_for_sale','')::numeric(14,2),
                case
                  when nullif(item ->> 'price','') is null then null::numeric(14,2)
                  when nullif(item ->> 'quantity','') is null then null::numeric(14,2)
                  else round(
                    (nullif(item ->> 'price','')::numeric * nullif(item ->> 'quantity','')::numeric),
                    2
                  )::numeric(14,2)
                end
              )                                                                  as gross_amount,

              nullif(fin.payload::jsonb ->> 'sale_commission','')::numeric(14,2) as commission_amount,

              nullif(fin.payload::jsonb ->> 'amount','')::numeric(14,2)          as net_amount,

              coalesce(
                nullif(fin.payload::jsonb ->> 'currency_code',''),
                nullif(item ->> 'currency_code',''),
                'RUB'
              )                                                                  as currency_code,

              coalesce(
                nullif(fin.payload::jsonb ->> 'operation_date','')::timestamptz::date,
                (p.payload::jsonb ->> 'shipment_date')::timestamptz::date
              )                                                                  as sale_date,

              case
                when fin.payload is null then (p.payload::jsonb ->> 'status')
                when (fin.payload::jsonb ->> 'operation_type') ilike '%%return%%' then 'RETURN'
                else 'SALE'
              end                                                                as operation_type
          from %2$s p
          join lateral jsonb_array_elements(p.payload::jsonb -> 'products') item on true

          left join lateral (
              select f.payload
              from %3$s f
              where f.account_id = p.account_id
                and (f.payload::jsonb -> 'posting' ->> 'posting_number') = (p.payload::jsonb ->> 'posting_number')
              order by nullif(f.payload::jsonb ->> 'operation_date','')::timestamptz desc nulls last
              limit 1
          ) fin on true

          where p.account_id = ?
            and p.request_id = ?
            and nullif(p.payload::jsonb ->> 'posting_number','') is not null
            and nullif(item ->> 'sku','') is not null
            and nullif(item ->> 'quantity','') is not null
            and nullif(p.payload::jsonb ->> 'shipment_date','') is not null
      ) t
      order by source_event_id, sale_date desc nulls last
      """.formatted(
        platform,
        RawTableNames.RAW_OZON_POSTINGS_FBS,
        RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
