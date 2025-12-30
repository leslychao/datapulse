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
      with source as (
          %s
      ),
      deleted as (
          delete from fact_commission f
          using source s
          where f.account_id      = s.account_id
            and f.source_platform = s.source_platform
            and f.operation_date  = s.operation_date
            and f.order_id is not distinct from s.order_id
            and f.commission_type = s.commission_type
      )
      insert into fact_commission (
          account_id,
          source_platform,
          order_id,
          operation_date,
          commission_type,
          amount,
          currency,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          order_id,
          operation_date,
          commission_type,
          amount,
          currency,
          now(),
          now()
      from source;
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        select
            account_id,
            source_platform,
            order_id,
            operation_date,
            commission_type,
            -sum(amount_signed) as amount,
            max(currency)       as currency
        from (
            select
                r.account_id                                   as account_id,
                '%1$s'                                         as source_platform,

                nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','')
                                                             as order_id,

                (r.payload::jsonb ->> 'operation_date')::timestamptz::date
                                                             as operation_date,

                nullif(r.payload::jsonb ->> 'type','')        as commission_type,

                nullif(r.payload::jsonb ->> 'sale_commission','')::numeric
                                                             as amount_signed,

                coalesce(
                    nullif(r.payload::jsonb ->> 'currency_code',''),
                    'RUB'
                )                                             as currency
            from %2$s r
            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'operation_date','')  is not null
              and nullif(r.payload::jsonb ->> 'sale_commission','') is not null
        ) s
        group by
            account_id,
            source_platform,
            order_id,
            operation_date,
            commission_type
        having sum(amount_signed) <> 0
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
        select
            account_id,
            source_platform,
            order_id,
            operation_date,
            commission_type,
            sum(amount) as amount,
            max(currency) as currency
        from (
            select
                r.account_id                              as account_id,
                '%1$s'                                    as source_platform,

                nullif(r.payload::jsonb ->> 'srid','')   as order_id,

                (r.payload::jsonb ->> 'rr_dt')::date     as operation_date,

                'SALES_COMMISSION'                       as commission_type,

                abs(
                    nullif(
                        r.payload::jsonb ->> 'ppvz_sales_commission',
                        ''
                    )::numeric
                )                                        as amount,

                coalesce(
                    nullif(r.payload::jsonb ->> 'currency_name',''),
                    'RUB'
                )                                        as currency

            from %2$s r
            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'rr_dt','') is not null
              and nullif(r.payload::jsonb ->> 'srid','')  is not null
              and nullif(r.payload::jsonb ->> 'ppvz_sales_commission','') is not null
        ) s
        group by
            account_id,
            source_platform,
            order_id,
            operation_date,
            commission_type
        having sum(amount) <> 0
        """.formatted(
        platform,
        RawTableNames.RAW_WB_SALES_REPORT_DETAIL
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
