package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.FinanceFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FinanceFactJdbcRepository implements FinanceFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into datapulse.fact_finance (
          account_id,
          source_platform,
          order_id,
          finance_date,
          currency,
          revenue_gross,
          seller_discount_amount,
          marketplace_commission_amount,
          acquiring_commission_amount,
          logistics_cost_amount,
          penalties_amount,
          marketing_cost_amount,
          compensation_amount,
          refund_amount,
          net_payout,
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
          acquiring_commission_amount,
          logistics_cost_amount,
          penalties_amount,
          marketing_cost_amount,
          compensation_amount,
          refund_amount,
          net_payout,
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
          acquiring_commission_amount   = excluded.acquiring_commission_amount,
          logistics_cost_amount         = excluded.logistics_cost_amount,
          penalties_amount              = excluded.penalties_amount,
          marketing_cost_amount         = excluded.marketing_cost_amount,
          compensation_amount           = excluded.compensation_amount,
          refund_amount                 = excluded.refund_amount,
          net_payout                    = excluded.net_payout,
          updated_at                    = now();
      """;

  private static final String WB_FINANCE_SELECT = """
      with daily_finance as (
        select
            :accountId                                                     as account_id,
            :sourcePlatform                                                as source_platform,
            r.payload::jsonb ->> 'srid'                                   as order_id,
            (r.payload::jsonb ->> 'rr_dt')::timestamptz::date             as finance_date,
            case
              when lower(coalesce(nullif(r.payload::jsonb ->> 'currency_name', ''), 'rub')) in ('руб', 'rub', 'rur')
                then 'RUB'
              else upper(coalesce(nullif(r.payload::jsonb ->> 'currency_name', ''), 'RUB'))
            end                                                           as currency,

            sum(
              coalesce(nullif(r.payload::jsonb ->> 'ppvz_for_pay', '')::numeric, 0)
            )                                                             as cash_flow_amount,

            sum(
              greatest(coalesce(nullif(r.payload::jsonb ->> 'retail_amount', '')::numeric, 0), 0)
            )                                                             as revenue_gross,

            sum(
              abs(coalesce(nullif(r.payload::jsonb ->> 'product_discount_for_report', '')::numeric, 0))
              + abs(coalesce(nullif(r.payload::jsonb ->> 'supplier_promo', '')::numeric, 0))
              + abs(coalesce(nullif(r.payload::jsonb ->> 'seller_promo_discount', '')::numeric, 0))
            )                                                             as seller_discount_amount,

            sum(
              abs(coalesce(nullif(r.payload::jsonb ->> 'return_amount', '')::numeric, 0))
            )                                                             as refund_amount,

            sum(
              abs(coalesce(nullif(r.payload::jsonb ->> 'additional_payment', '')::numeric, 0))
              + abs(coalesce(nullif(r.payload::jsonb ->> 'installment_cofinancing_amount', '')::numeric, 0))
            )                                                             as compensation_amount

        from %1$s r
        where r.account_id = :accountId
          and r.request_id = :requestId
          and r.marketplace = 'WILDBERRIES'
          and nullif(r.payload::jsonb ->> 'srid', '') is not null
        group by
          r.payload::jsonb ->> 'srid',
          (r.payload::jsonb ->> 'rr_dt')::timestamptz::date,
          case
            when lower(coalesce(nullif(r.payload::jsonb ->> 'currency_name', ''), 'rub')) in ('руб', 'rub', 'rur')
              then 'RUB'
            else upper(coalesce(nullif(r.payload::jsonb ->> 'currency_name', ''), 'RUB'))
          end
        having
          abs(sum(coalesce(nullif(r.payload::jsonb ->> 'ppvz_for_pay', '')::numeric, 0))) <> 0
          or sum(greatest(coalesce(nullif(r.payload::jsonb ->> 'retail_amount', '')::numeric, 0), 0)) <> 0
          or abs(sum(coalesce(nullif(r.payload::jsonb ->> 'return_amount', '')::numeric, 0))) <> 0
          or abs(sum(
                coalesce(nullif(r.payload::jsonb ->> 'product_discount_for_report', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'supplier_promo', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'seller_promo_discount', '')::numeric, 0)
          )) <> 0
          or abs(sum(
                coalesce(nullif(r.payload::jsonb ->> 'additional_payment', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'installment_cofinancing_amount', '')::numeric, 0)
          )) <> 0
      ),

      commission as (
        select
            fc.account_id,
            fc.source_platform,
            fc.order_id,
            fc.operation_date                                             as finance_date,
            sum(
              case
                when fc.commission_type = 'SALE_COMMISSION'
                  then fc.commission_charge_amount - fc.commission_refund_amount
                else 0
              end
            )                                                             as marketplace_commission_amount,
            sum(
              case
                when fc.commission_type = 'ACQUIRING_COMMISSION'
                  then fc.commission_charge_amount - fc.commission_refund_amount
                else 0
              end
            )                                                             as acquiring_commission_amount
        from datapulse.fact_commission fc
        where fc.account_id      = :accountId
          and fc.source_platform = :sourcePlatform
        group by
          fc.account_id,
          fc.source_platform,
          fc.order_id,
          fc.operation_date
      ),

      logistics as (
        select
            fl.account_id,
            fl.source_platform,
            fl.order_id,
            fl.operation_date                                             as finance_date,
            sum(fl.logistics_charge_amount - fl.logistics_refund_amount)  as logistics_cost_amount
        from datapulse.fact_logistics_costs fl
        where fl.account_id      = :accountId
          and fl.source_platform = :sourcePlatform
        group by
          fl.account_id,
          fl.source_platform,
          fl.order_id,
          fl.operation_date
      ),

      penalties as (
        -- Penalities aggregated by date.  The fact_penalties table stores
        -- separate charge and refund components to preserve sign.  We
        -- compute the net penalty amount here.  Positive values denote
        -- costs, negative values denote refunds.
        select
            fp.account_id,
            fp.source_platform,
            fp.order_id,
            fp.penalty_date                                               as finance_date,
            sum(fp.penalty_charge_amount - fp.penalty_refund_amount)      as penalties_amount
        from datapulse.fact_penalties fp
        where fp.account_id      = :accountId
          and fp.source_platform = :sourcePlatform
        group by
          fp.account_id,
          fp.source_platform,
          fp.order_id,
          fp.penalty_date
      ),

      marketing as (
        select
            fm.account_id,
            fm.source_platform,
            fm.order_id,
            fm.operation_date                                             as finance_date,
            sum(fm.marketing_charge_amount - fm.marketing_refund_amount)  as marketing_cost_amount
        from datapulse.fact_marketing_costs fm
        where fm.account_id      = :accountId
          and fm.source_platform = :sourcePlatform
        group by
          fm.account_id,
          fm.source_platform,
          fm.order_id,
          fm.operation_date
      )

      select
          df.account_id,
          df.source_platform,
          df.order_id,
          df.finance_date,
          df.currency,
          df.revenue_gross,
          df.seller_discount_amount,
          coalesce(c.marketplace_commission_amount, 0) as marketplace_commission_amount,
          coalesce(c.acquiring_commission_amount, 0)   as acquiring_commission_amount,
          coalesce(l.logistics_cost_amount, 0)         as logistics_cost_amount,
          coalesce(p.penalties_amount, 0)              as penalties_amount,
          coalesce(m.marketing_cost_amount, 0)         as marketing_cost_amount,
          df.compensation_amount,
          df.refund_amount,
          df.cash_flow_amount                          as net_payout
      from daily_finance df
      left join commission c
        on c.account_id      = df.account_id
       and c.source_platform = df.source_platform
       and c.order_id        = df.order_id
       and c.finance_date    = df.finance_date
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
      left join marketing m
        on m.account_id      = df.account_id
       and m.source_platform = df.source_platform
       and m.order_id        = df.order_id
       and m.finance_date    = df.finance_date
      """.formatted(RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

  private static final String OZON_FINANCE_SELECT = """
      with daily_finance as (
        select
            :accountId                                                           as account_id,
            :sourcePlatform                                                      as source_platform,
            nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '')      as order_id,
            (r.payload::jsonb ->> 'operation_date')::timestamptz::date          as finance_date,
            'RUB'                                                                as currency,

            sum(
              coalesce(nullif(r.payload::jsonb ->> 'amount', '')::numeric, 0)
            )                                                                    as cash_flow_amount,

            sum(
              case
                when r.payload::jsonb ->> 'type' = 'orders'
                  then greatest(coalesce(nullif(r.payload::jsonb ->> 'accruals_for_sale', '')::numeric, 0), 0)
                else 0
              end
            )                                                                    as revenue_gross,

            0::numeric                                                           as seller_discount_amount,

            sum(
              case
                when r.payload::jsonb ->> 'type' = 'returns'
                 and r.payload::jsonb ->> 'operation_type' = 'ClientReturnAgentOperation'
                  then abs(coalesce(nullif(r.payload::jsonb ->> 'accruals_for_sale', '')::numeric, 0))
                else 0
              end
            )                                                                    as refund_amount,

            sum(
              case
                when r.payload::jsonb ->> 'type' = 'compensation'
                  then abs(coalesce(nullif(r.payload::jsonb ->> 'amount', '')::numeric, 0))
                else 0
              end
            )                                                                    as compensation_amount

        from %1$s r
        where r.account_id = :accountId
          and r.request_id = :requestId
          and r.marketplace = 'OZON'
          and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '') is not null
        group by
          nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', ''),
          (r.payload::jsonb ->> 'operation_date')::timestamptz::date
      ),

      commission as (
        select
            fc.account_id,
            fc.source_platform,
            fc.order_id,
            fc.operation_date                                             as finance_date,
            sum(
              case
                when fc.commission_type = 'SALE_COMMISSION'
                  then fc.commission_charge_amount - fc.commission_refund_amount
                else 0
              end
            )                                                             as marketplace_commission_amount,
            sum(
              case
                when fc.commission_type = 'ACQUIRING_COMMISSION'
                  then fc.commission_charge_amount - fc.commission_refund_amount
                else 0
              end
            )                                                             as acquiring_commission_amount
        from datapulse.fact_commission fc
        where fc.account_id      = :accountId
          and fc.source_platform = :sourcePlatform
        group by
          fc.account_id,
          fc.source_platform,
          fc.order_id,
          fc.operation_date
      ),

      logistics as (
        select
            fl.account_id,
            fl.source_platform,
            fl.order_id,
            fl.operation_date                                             as finance_date,
            sum(fl.logistics_charge_amount - fl.logistics_refund_amount)  as logistics_cost_amount
        from datapulse.fact_logistics_costs fl
        where fl.account_id      = :accountId
          and fl.source_platform = :sourcePlatform
        group by
          fl.account_id,
          fl.source_platform,
          fl.order_id,
          fl.operation_date
      ),

      penalties as (
        -- Penalities aggregated by date.  Positive values denote costs,
        -- negative denote refunds.  We derive net amount from charge
        -- minus refund components.
        select
            fp.account_id,
            fp.source_platform,
            fp.order_id,
            fp.penalty_date                                               as finance_date,
            sum(fp.penalty_charge_amount - fp.penalty_refund_amount)      as penalties_amount
        from datapulse.fact_penalties fp
        where fp.account_id      = :accountId
          and fp.source_platform = :sourcePlatform
        group by
          fp.account_id,
          fp.source_platform,
          fp.order_id,
          fp.penalty_date
      ),

      marketing as (
        select
            fm.account_id,
            fm.source_platform,
            fm.order_id,
            fm.operation_date                                             as finance_date,
            sum(fm.marketing_charge_amount - fm.marketing_refund_amount)  as marketing_cost_amount
        from datapulse.fact_marketing_costs fm
        where fm.account_id      = :accountId
          and fm.source_platform = :sourcePlatform
        group by
          fm.account_id,
          fm.source_platform,
          fm.order_id,
          fm.operation_date
      )

      select
          df.account_id,
          df.source_platform,
          df.order_id,
          df.finance_date,
          df.currency,
          df.revenue_gross,
          df.seller_discount_amount,
          coalesce(c.marketplace_commission_amount, 0) as marketplace_commission_amount,
          coalesce(c.acquiring_commission_amount, 0)   as acquiring_commission_amount,
          coalesce(l.logistics_cost_amount, 0)         as logistics_cost_amount,
          coalesce(p.penalties_amount, 0)              as penalties_amount,
          coalesce(m.marketing_cost_amount, 0)         as marketing_cost_amount,
          df.compensation_amount,
          df.refund_amount,
          df.cash_flow_amount                          as net_payout
      from daily_finance df
      left join commission c
        on c.account_id      = df.account_id
       and c.source_platform = df.source_platform
       and c.order_id        = df.order_id
       and c.finance_date    = df.finance_date
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
      left join marketing m
        on m.account_id      = df.account_id
       and m.source_platform = df.source_platform
       and m.order_id        = df.order_id
       and m.finance_date    = df.finance_date
      """.formatted(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void upsertFromWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String sourcePlatform = MarketplaceType.WILDBERRIES.tag();
    String sql = UPSERT_TEMPLATE.formatted(WB_FINANCE_SELECT);

    MapSqlParameterSource parameters = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("sourcePlatform", sourcePlatform);

    int rows = jdbcTemplate.update(sql, parameters);

    log.info(
        "Finance facts upserted (WB): accountId={}, sourcePlatform={}, requestId={}, rows={}",
        accountId,
        sourcePlatform,
        requestId,
        rows
    );
  }

  @Override
  public void upsertFromOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    String sourcePlatform = MarketplaceType.OZON.tag();
    String sql = UPSERT_TEMPLATE.formatted(OZON_FINANCE_SELECT);

    MapSqlParameterSource parameters = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("sourcePlatform", sourcePlatform);

    int rows = jdbcTemplate.update(sql, parameters);

    log.info(
        "Finance facts upserted (Ozon): accountId={}, sourcePlatform={}, requestId={}, rows={}",
        accountId,
        sourcePlatform,
        requestId,
        rows
    );
  }
}
