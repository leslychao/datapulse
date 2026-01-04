package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.FinanceFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FinanceFactJdbcRepository implements FinanceFactRepository {

  private static final Logger log = LoggerFactory.getLogger(FinanceFactJdbcRepository.class);

  private static final String UPSERT_TEMPLATE = """
      insert into fact_finance (
          account_id,
          source_platform,
          order_id,
          finance_date,
          currency,
          revenue_gross,
          seller_discount_amount,
          marketplace_commission_amount,
          logistics_cost_amount,
          penalties_amount,
          marketing_cost_amount,
          compensation_amount,
          refund_amount,
          net_payout,
          is_closed,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          order_id,
          finance_date,
          currency,
          revenue_gross,
          seller_discount_amount,
          marketplace_commission_amount,
          logistics_cost_amount,
          penalties_amount,
          marketing_cost_amount,
          compensation_amount,
          refund_amount,
          net_payout,
          false as is_closed,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, order_id, finance_date)
      do update
      set
          currency                      = excluded.currency,
          revenue_gross                 = excluded.revenue_gross,
          seller_discount_amount        = excluded.seller_discount_amount,
          marketplace_commission_amount = excluded.marketplace_commission_amount,
          logistics_cost_amount         = excluded.logistics_cost_amount,
          penalties_amount              = excluded.penalties_amount,
          marketing_cost_amount         = excluded.marketing_cost_amount,
          compensation_amount           = excluded.compensation_amount,
          refund_amount                 = excluded.refund_amount,
          net_payout                    = excluded.net_payout,
          updated_at                    = now();
      """;

  private static final String WB_FINANCE_SELECT = """
      with revenue as (
        select
            :accountId                                                                as account_id,
            :sourcePlatform                                                           as source_platform,
            r.payload::jsonb ->> 'srid'                                              as order_id,
            (r.payload::jsonb ->> 'rr_dt')::date                                     as finance_date,
            coalesce(r.payload::jsonb ->> 'currency_name', 'руб')                    as currency,
            sum(coalesce(nullif(r.payload::jsonb ->> 'retail_amount', '')::numeric, 0)) as revenue_gross,
            sum(
              coalesce(nullif(r.payload::jsonb ->> 'product_discount_for_report', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'supplier_promo', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'seller_promo_discount', '')::numeric, 0)
            )                                                                        as seller_discount_amount,
            sum(coalesce(nullif(r.payload::jsonb ->> 'return_amount', '')::numeric, 0))
                                                                                     as refund_amount,
            sum(coalesce(nullif(r.payload::jsonb ->> 'additional_payment', '')::numeric, 0))
                                                                                     as compensation_amount
        from %1$s r
        where r.account_id = :accountId
          and r.request_id = :requestId
          and r.marketplace = 'WILDBERRIES'
        group by
          r.payload::jsonb ->> 'srid',
          (r.payload::jsonb ->> 'rr_dt')::date,
          coalesce(r.payload::jsonb ->> 'currency_name', 'руб')
      ),
      commission as (
        select
            fc.account_id,
            fc.source_platform,
            fc.order_id,
            sum(
              case
                when fc.commission_type in ('SALE_COMMISSION', 'ACQUIRING_FEE', 'SERVICE_FEE', 'MARKETING_COMMISSION')
                  then fc.amount
                else 0
              end
            ) as marketplace_commission_amount,
            sum(
              case
                when fc.commission_type = 'MARKETING_COMMISSION'
                  then fc.amount
                else 0
              end
            ) as marketing_cost_amount
        from fact_commission fc
        where fc.account_id      = :accountId
          and fc.source_platform = :sourcePlatform
        group by
          fc.account_id,
          fc.source_platform,
          fc.order_id
      ),
      logistics as (
        select
            fl.account_id,
            fl.source_platform,
            fl.order_id,
            sum(fl.amount) as logistics_cost_amount
        from fact_logistics_costs fl
        where fl.account_id      = :accountId
          and fl.source_platform = :sourcePlatform
        group by
          fl.account_id,
          fl.source_platform,
          fl.order_id
      ),
      penalties as (
        select
            fp.account_id,
            fp.source_platform,
            fp.order_id,
            sum(fp.amount) as penalties_amount
        from fact_penalties fp
        where fp.account_id      = :accountId
          and fp.source_platform = :sourcePlatform
        group by
          fp.account_id,
          fp.source_platform,
          fp.order_id
      )
      select
          r.account_id,
          r.source_platform,
          r.order_id,
          r.finance_date,
          r.currency,
          r.revenue_gross,
          r.seller_discount_amount,
          coalesce(c.marketplace_commission_amount, 0) as marketplace_commission_amount,
          coalesce(l.logistics_cost_amount, 0)         as logistics_cost_amount,
          coalesce(p.penalties_amount, 0)              as penalties_amount,
          coalesce(c.marketing_cost_amount, 0)         as marketing_cost_amount,
          r.compensation_amount,
          r.refund_amount,
          (
            r.revenue_gross
            - r.seller_discount_amount
            - coalesce(c.marketplace_commission_amount, 0)
            - coalesce(l.logistics_cost_amount, 0)
            - coalesce(p.penalties_amount, 0)
            - coalesce(c.marketing_cost_amount, 0)
            + r.compensation_amount
            - r.refund_amount
          ) as net_payout
      from revenue r
      left join commission c
        on c.account_id      = r.account_id
       and c.source_platform = r.source_platform
       and c.order_id        = r.order_id
      left join logistics l
        on l.account_id      = r.account_id
       and l.source_platform = r.source_platform
       and l.order_id        = r.order_id
      left join penalties p
        on p.account_id      = r.account_id
       and p.source_platform = r.source_platform
       and p.order_id        = r.order_id
      """.formatted(RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

  private static final String OZON_FINANCE_SELECT = """
      with daily_finance as (
        select
            :accountId                                                                                 as account_id,
            :sourcePlatform                                                                            as source_platform,
            nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '')                            as order_id,
            (r.payload::jsonb ->> 'operation_date')::timestamptz::date                                as finance_date,
            'RUB'                                                                                      as currency,

            -- Выручка: только операции type = 'orders'
            sum(
              case
                when r.payload::jsonb ->> 'type' = 'orders'
                  then coalesce(nullif(r.payload::jsonb ->> 'accruals_for_sale', '')::numeric, 0)
                else 0
              end
            )                                                                                          as revenue_gross,

            -- Пока нет явного поля в API → 0, позже подтянем из postings.financial_data
            0::numeric                                                                                 as seller_discount_amount,

            -- Комиссия: продажа = +abs(sale_commission), возврат = -abs(sale_commission)
            sum(
              case
                when r.payload::jsonb ->> 'type' = 'orders' then
                  abs(coalesce(nullif(r.payload::jsonb ->> 'sale_commission', '')::numeric, 0))
                when r.payload::jsonb ->> 'type' = 'returns' then
                  -abs(coalesce(nullif(r.payload::jsonb ->> 'sale_commission', '')::numeric, 0))
                else 0
              end
            )                                                                                          as marketplace_commission_amount,

            -- Возврат покупателю: только «денежный» возврат
            sum(
              case
                when r.payload::jsonb ->> 'type' = 'returns'
                 and r.payload::jsonb ->> 'operation_type' = 'ClientReturnAgentOperation'
                  then abs(coalesce(nullif(r.payload::jsonb ->> 'amount', '')::numeric, 0))
                else 0
              end
            )                                                                                          as refund_amount,

            -- Компенсации от Ozon (если есть)
            sum(
              case
                when r.payload::jsonb ->> 'type' = 'compensation'
                  then abs(coalesce(nullif(r.payload::jsonb ->> 'amount', '')::numeric, 0))
                else 0
              end
            )                                                                                          as compensation_amount

        from %1$s r
        where r.account_id = :accountId
          and r.request_id = :requestId
          and r.marketplace = 'OZON'
          and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '') is not null
        group by
          nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', ''),
          (r.payload::jsonb ->> 'operation_date')::timestamptz::date
      ),

      logistics as (
        select
            fl.account_id,
            fl.source_platform,
            fl.order_id,
            fl.operation_date                                                  as finance_date,
            sum(fl.amount)                                                     as logistics_cost_amount
        from fact_logistics_costs fl
        where fl.account_id      = :accountId
          and fl.source_platform = :sourcePlatform
        group by
          fl.account_id,
          fl.source_platform,
          fl.order_id,
          fl.operation_date
      ),

      penalties as (
        select
            fp.account_id,
            fp.source_platform,
            fp.order_id,
            fp.penalty_date                                                    as finance_date,
            sum(fp.amount)                                                     as penalties_amount
        from fact_penalties fp
        where fp.account_id      = :accountId
          and fp.source_platform = :sourcePlatform
        group by
          fp.account_id,
          fp.source_platform,
          fp.order_id,
          fp.penalty_date
      )

      select
          df.account_id,
          df.source_platform,
          df.order_id,
          df.finance_date,
          df.currency,

          df.revenue_gross,
          df.seller_discount_amount,
          df.marketplace_commission_amount,
          coalesce(l.logistics_cost_amount, 0) as logistics_cost_amount,
          coalesce(p.penalties_amount, 0)      as penalties_amount,
          0::numeric                           as marketing_cost_amount,
          df.compensation_amount,
          df.refund_amount,

          (
            df.revenue_gross
            - df.seller_discount_amount
            - df.marketplace_commission_amount
            - coalesce(l.logistics_cost_amount, 0)
            - coalesce(p.penalties_amount, 0)
            - 0::numeric
            + df.compensation_amount
            - df.refund_amount
          ) as net_payout

      from daily_finance df
      left join logistics l
        on l.account_id      = df.account_id
       and l.source_platform = df.source_platform
       and l.order_id        = df.order_id
       and l.finance_date    = df.finance_date
      left join penalties p
        on p.account_id      = df.account_id
       and p.source_platform = df.source_platform
       and p.order_id        = df.order_id
       and p.finance_date    = df.finance_date
      """.formatted(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void upsertFromWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String sourcePlatform = MarketplaceType.WILDBERRIES.tag();
    String sql = UPSERT_TEMPLATE.formatted(WB_FINANCE_SELECT);
    MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("accountId", accountId)
        .addValue("requestId", requestId).addValue("sourcePlatform", sourcePlatform);
    int rows = jdbcTemplate.update(sql, parameters);
    log.info("Finance facts upserted (WB): accountId={}, sourcePlatform={}, requestId={}, rows={}",
        accountId, sourcePlatform, requestId, rows);
  }

  @Override
  public void upsertFromOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String sourcePlatform = MarketplaceType.OZON.tag();
    String sql = UPSERT_TEMPLATE.formatted(OZON_FINANCE_SELECT);
    MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("accountId", accountId)
        .addValue("requestId", requestId).addValue("sourcePlatform", sourcePlatform);
    int rows = jdbcTemplate.update(sql, parameters);
    log.info(
        "Finance facts upserted (Ozon): accountId={}, sourcePlatform={}, requestId={}, rows={}",
        accountId, sourcePlatform, requestId, rows);
  }
}
