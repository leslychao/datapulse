package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.CommissionFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommissionFactJdbcRepository implements CommissionFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_commission (
          account_id,
          source_platform,
          order_id,
          dim_product_id,
          operation_date,
          commission_type,
          amount,
          currency,
          amount_raw,
          amount_abs,
          commission_kind,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          order_id,
          dim_product_id,
          operation_date,
          commission_type,
          amount,
          currency,
          amount_raw,
          amount_abs,
          commission_kind,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, operation_date, order_id, commission_type, dim_product_id)
      do update
      set
          amount          = excluded.amount,
          currency        = excluded.currency,
          amount_raw      = excluded.amount_raw,
          amount_abs      = excluded.amount_abs,
          commission_kind = excluded.commission_kind,
          updated_at      = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        with source_rows as (
            select
                r.account_id                                   as account_id,
                '%1$s'                                         as source_platform,
                nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','')
                                                             as order_id,
                dp.id                                          as dim_product_id,
                (r.payload::jsonb ->> 'operation_date')::timestamptz::date
                                                             as operation_date,
                nullif(r.payload::jsonb ->> 'sale_commission','')::numeric
                  / nullif(it.item_count, 0)                   as amount_signed,
                coalesce(
                    nullif(r.payload::jsonb ->> 'currency_code',''),
                    'RUB'
                )                                              as currency,
                fr.id is not null                              as is_return
            from %2$s r
            join lateral (
                select
                    item,
                    count(*) over () as item_count
                from jsonb_array_elements(r.payload::jsonb -> 'items') item
            ) it on true
            left join dim_product dp
              on dp.account_id        = r.account_id
             and dp.source_platform   = '%1$s'
             and dp.source_product_id = nullif(it.item ->> 'sku','')
            left join fact_returns fr
              on fr.account_id      = r.account_id
             and fr.source_platform = '%1$s'
             and fr.order_id        = nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','')
             and fr.dim_product_id  = dp.id
            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'operation_date','')  is not null
              and nullif(r.payload::jsonb ->> 'sale_commission','') is not null
              and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','') is not null
              and dp.id is not null
        ),
        aggregated as (
            select
                account_id,
                source_platform,
                order_id,
                dim_product_id,
                operation_date,
                bool_or(is_return) as has_return,
                sum(amount_signed) as amount_signed,
                max(currency)      as currency
            from source_rows
            group by
                account_id,
                source_platform,
                order_id,
                dim_product_id,
                operation_date
        )
        select
            account_id,
            source_platform,
            order_id,
            dim_product_id,
            operation_date,
            case
              when has_return then 'SALES_COMMISSION_REFUND'
              else 'SALES_COMMISSION_CHARGE'
            end                         as commission_type,
            abs(amount_signed)          as amount,
            currency                    as currency,
            amount_signed               as amount_raw,
            abs(amount_signed)          as amount_abs,
            'SALES'                     as commission_kind
        from aggregated
        where amount_signed <> 0
        """.formatted(
        platform,
        RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        with source_rows as (
            select
                r.account_id                              as account_id,
                '%1$s'                                    as source_platform,
                nullif(r.payload::jsonb ->> 'srid','')   as order_id,
                dp.id                                     as dim_product_id,
                (r.payload::jsonb ->> 'rr_dt')::date     as operation_date,
                nullif(
                    r.payload::jsonb ->> 'ppvz_sales_commission',
                    ''
                )::numeric                               as amount_signed,
                coalesce(
                    nullif(r.payload::jsonb ->> 'currency_name',''),
                    'RUB'
                )                                        as currency,
                fr.id is not null                        as is_return
            from %2$s r
            join dim_product dp
              on dp.account_id        = r.account_id
             and dp.source_platform   = '%1$s'
             and dp.source_product_id = nullif(r.payload::jsonb ->> 'nm_id','')
            left join fact_returns fr
              on fr.account_id      = r.account_id
             and fr.source_platform = '%1$s'
             and fr.order_id        = nullif(r.payload::jsonb ->> 'srid','')
             and fr.dim_product_id  = dp.id
            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'rr_dt','')  is not null
              and nullif(r.payload::jsonb ->> 'srid','')   is not null
              and nullif(r.payload::jsonb ->> 'nm_id','')  is not null
              and nullif(r.payload::jsonb ->> 'ppvz_sales_commission','') is not null
        ),
        aggregated as (
            select
                account_id,
                source_platform,
                order_id,
                dim_product_id,
                operation_date,
                bool_or(is_return) as has_return,
                sum(amount_signed) as amount_signed,
                max(currency)      as currency
            from source_rows
            group by
                account_id,
                source_platform,
                order_id,
                dim_product_id,
                operation_date
        )
        select
            account_id,
            source_platform,
            order_id,
            dim_product_id,
            operation_date,
            case
              when has_return then 'SALES_COMMISSION_REFUND'
              else 'SALES_COMMISSION_CHARGE'
            end                         as commission_type,
            abs(amount_signed)          as amount,
            currency                    as currency,
            amount_signed               as amount_raw,
            abs(amount_signed)          as amount_abs,
            'SALES'                     as commission_kind
        from aggregated
        where amount_signed <> 0
        """.formatted(
        platform,
        RawTableNames.RAW_WB_SALES_REPORT_DETAIL
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
