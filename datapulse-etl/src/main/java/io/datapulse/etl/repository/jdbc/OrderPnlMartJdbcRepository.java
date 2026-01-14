package io.datapulse.etl.repository.jdbc;

import io.datapulse.etl.repository.OrderPnlMartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class OrderPnlMartJdbcRepository implements OrderPnlMartRepository {

  private static final String UPSERT_SQL = """
      with
          finance_by_order as (
              select
                  account_id,
                  source_platform,
                  order_id,
                  case
                    when count(distinct currency) = 1 then min(currency)
                    else 'MIXED'
                  end                                as currency,
                  min(finance_date)                  as first_finance_date,
                  max(finance_date)                  as last_finance_date,
                  sum(revenue_gross)                 as revenue_gross,
                  sum(seller_discount_amount)        as seller_discount_amount,
                  sum(marketplace_commission_amount) as marketplace_commission_amount,
                  sum(acquiring_commission_amount)   as acquiring_commission_amount,
                  sum(logistics_cost_amount)         as logistics_cost_amount,
                  sum(penalties_amount)              as penalties_amount,
                  sum(marketing_cost_amount)         as marketing_cost_amount,
                  sum(compensation_amount)           as compensation_amount,
                  sum(refund_amount)                 as refund_amount,
                  sum(net_payout)                    as net_payout
              from datapulse.fact_finance
              where account_id = :accountId
              group by account_id, source_platform, order_id
          ),

          sales_by_order as (
              select
                  account_id,
                  source_platform,
                  order_id,
                  sum(case when quantity > 0 then quantity else 0 end)  as items_sold_count,
                  sum(case when quantity < 0 then -quantity else 0 end) as returned_from_sales
              from datapulse.fact_sales s
              where s.account_id = :accountId
                and exists (
                    select 1
                    from finance_by_order f
                    where f.account_id = s.account_id
                      and f.source_platform = s.source_platform
                      and f.order_id = s.order_id
                )
              group by account_id, source_platform, order_id
          ),

          returns_by_order as (
              select
                  account_id,
                  source_platform,
                  order_id,
                  sum(quantity) as returned_items_count
              from datapulse.fact_returns r
              where r.account_id = :accountId
                and exists (
                    select 1
                    from finance_by_order f
                    where f.account_id = r.account_id
                      and f.source_platform = r.source_platform
                      and f.order_id = r.order_id
                )
              group by account_id, source_platform, order_id
          ),

          qty_by_order as (
              select
                  coalesce(s.account_id, r.account_id)           as account_id,
                  coalesce(s.source_platform, r.source_platform) as source_platform,
                  coalesce(s.order_id, r.order_id)               as order_id,
                  coalesce(s.items_sold_count, 0)                as items_sold_count,
                  coalesce(s.returned_from_sales, 0)
                      + coalesce(r.returned_items_count, 0)      as returned_items_count
              from sales_by_order s
                       full join returns_by_order r
                                 on r.account_id = s.account_id
                                 and r.source_platform = s.source_platform
                                 and r.order_id = s.order_id
          )

      insert into datapulse.mart_order_pnl (
          account_id,
          source_platform,
          order_id,
          currency,
          first_finance_date,
          last_finance_date,
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
          pnl_amount,
          items_sold_count,
          returned_items_count,
          is_returned,
          has_penalties,
          created_at,
          updated_at
      )
      select
          f.account_id,
          f.source_platform,
          f.order_id,
          f.currency,
          f.first_finance_date,
          f.last_finance_date,
          f.revenue_gross,
          f.seller_discount_amount,

          case
            when f.source_platform = 'ozon' and f.revenue_gross = f.refund_amount and f.revenue_gross <> 0
              then 0
            else f.marketplace_commission_amount
          end                                                       as marketplace_commission_amount,

          case
            when f.source_platform = 'ozon' and f.revenue_gross = f.refund_amount and f.revenue_gross <> 0
              then 0
            else f.acquiring_commission_amount
          end                                                       as acquiring_commission_amount,

          f.logistics_cost_amount,
          f.penalties_amount,
          f.marketing_cost_amount,
          f.compensation_amount,
          f.refund_amount,
          f.net_payout,

          (
              f.revenue_gross
              - case
                  when f.source_platform = 'ozon' and f.revenue_gross = f.refund_amount and f.revenue_gross <> 0
                    then 0
                  else f.marketplace_commission_amount
                end
              - case
                  when f.source_platform = 'ozon' and f.revenue_gross = f.refund_amount and f.revenue_gross <> 0
                    then 0
                  else f.acquiring_commission_amount
                end
              - f.logistics_cost_amount
              - f.penalties_amount
              - f.marketing_cost_amount
              - f.refund_amount
              + f.compensation_amount
          )                                                         as pnl_amount,

          coalesce(q.items_sold_count, 0)                           as items_sold_count,
          coalesce(q.returned_items_count, 0)                       as returned_items_count,
          (coalesce(q.returned_items_count, 0) <> 0 or f.refund_amount <> 0) as is_returned,
          (f.penalties_amount <> 0)                                 as has_penalties,
          now(),
          now()
      from finance_by_order f
               left join qty_by_order q
                         on q.account_id = f.account_id
                         and q.source_platform = f.source_platform
                         and q.order_id = f.order_id
      on conflict (account_id, source_platform, order_id) do update
      set
          currency                      = excluded.currency,
          first_finance_date            = excluded.first_finance_date,
          last_finance_date             = excluded.last_finance_date,
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
          pnl_amount                    = excluded.pnl_amount,
          items_sold_count              = excluded.items_sold_count,
          returned_items_count          = excluded.returned_items_count,
          is_returned                   = excluded.is_returned,
          has_penalties                 = excluded.has_penalties,
          updated_at                    = now();
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void refresh(long accountId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId);

    int affected = jdbcTemplate.update(UPSERT_SQL, params);

    log.info(
        "Order PnL mart refreshed (full account): accountId={}, affectedRows={}",
        accountId,
        affected
    );
  }
}
