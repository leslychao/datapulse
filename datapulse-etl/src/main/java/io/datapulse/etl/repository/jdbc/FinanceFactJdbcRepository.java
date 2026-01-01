package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.FinanceFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FinanceFactJdbcRepository implements FinanceFactRepository {

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Override
  public void upsertFromWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String query = """
        with source_sales as (
            select distinct
                fs.id              as fact_sales_id,
                fs.account_id      as account_id,
                fs.source_platform as source_platform,
                fs.order_id        as order_id,
                fs.dim_product_id  as dim_product_id,
                fs.quantity        as quantity,
                fs.sale_date       as sale_date,
                fs.warehouse_id    as warehouse_id
            from fact_sales fs
            join dim_product dp
              on dp.id = fs.dim_product_id
            join %2$s r
              on r.account_id = fs.account_id
             and r.request_id = :requestId
             and nullif(r.payload::jsonb ->> 'srid','') = fs.order_id
             and nullif(r.payload::jsonb ->> 'nm_id','') = dp.source_product_id
            where fs.account_id = :accountId
              and fs.source_platform = '%1$s'
        ),
        finance_raw as (
            select
                ss.fact_sales_id as fact_sales_id,
                sum(
                    coalesce(
                        nullif(r.payload::jsonb ->> 'retail_price_withdisc_rub','')::numeric,
                        0
                    )
                ) as revenue_gross_amount,
                sum(
                    coalesce(
                        nullif(r.payload::jsonb ->> 'ppvz_for_pay','')::numeric,
                        0
                    )
                ) as payout_from_marketplace_raw,
                max(
                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_name',''),
                        'RUB'
                    )
                ) as currency,
                bool_or(
                    nullif(r.payload::jsonb ->> 'doc_type_name','') in (
                        'Возврат', 'Сторно продажи'
                    )
                ) as is_refund_raw
            from source_sales ss
            join dim_product dp
              on dp.id = ss.dim_product_id
            join %2$s r
              on r.account_id = ss.account_id
             and r.request_id = :requestId
             and nullif(r.payload::jsonb ->> 'srid','') = ss.order_id
             and nullif(r.payload::jsonb ->> 'nm_id','') = dp.source_product_id
            group by ss.fact_sales_id
        ),
        commission_costs as (
            select
                ss.fact_sales_id as fact_sales_id,
                sum(fc.amount)   as total_commission
            from source_sales ss
            left join fact_commission fc
              on fc.account_id = ss.account_id
             and fc.source_platform = ss.source_platform
             and fc.order_id = ss.order_id
            group by ss.fact_sales_id
        ),
        logistics_costs as (
            select
                ss.fact_sales_id as fact_sales_id,
                sum(fl.amount)   as total_logistics
            from source_sales ss
            left join fact_logistics_costs fl
              on fl.account_id      = ss.account_id
             and fl.source_platform = ss.source_platform
             and fl.operation_date  = ss.sale_date
             and fl.warehouse_id    = ss.warehouse_id
            group by ss.fact_sales_id
        ),
        product_costs as (
            select
                ss.fact_sales_id                  as fact_sales_id,
                sum(fpc.cost_value * ss.quantity) as total_product_cost
            from source_sales ss
            left join fact_product_cost fpc
              on fpc.account_id = ss.account_id
             and fpc.product_id = ss.dim_product_id
             and ss.sale_date >= fpc.valid_from
             and (fpc.valid_to is null or ss.sale_date <= fpc.valid_to)
            group by ss.fact_sales_id
        ),
        return_flags as (
            select
                ss.fact_sales_id           as fact_sales_id,
                bool_or(fr.id is not null) as has_returns
            from source_sales ss
            left join fact_returns fr
              on fr.account_id = ss.account_id
             and fr.source_platform = ss.source_platform
             and fr.order_id = ss.order_id
             and fr.dim_product_id = ss.dim_product_id
            group by ss.fact_sales_id
        ),
        finalized as (
            select
                ss.fact_sales_id                                            as fact_sales_id,
                coalesce(fr.revenue_gross_amount, 0)                         as revenue_gross_amount,
                (coalesce(fr.revenue_gross_amount, 0)
                    - coalesce(cc.total_commission, 0)
                    - coalesce(lc.total_logistics, 0)
                    - coalesce(pc.total_product_cost, 0)
                )                                                            as revenue_net_amount,
                coalesce(fr.payout_from_marketplace_raw, 0)                  as payout_from_marketplace,
                (coalesce(fr.payout_from_marketplace_raw, 0)
                    - coalesce(pc.total_product_cost, 0)
                )                                                            as mp_based_net,
                coalesce(fr.currency, 'RUB')                                 as currency,
                coalesce(fr.is_refund_raw, false) or coalesce(rf.has_returns, false)
                                                                             as is_refund
            from source_sales ss
            left join finance_raw fr    on fr.fact_sales_id = ss.fact_sales_id
            left join commission_costs cc on cc.fact_sales_id = ss.fact_sales_id
            left join logistics_costs lc  on lc.fact_sales_id = ss.fact_sales_id
            left join product_costs pc   on pc.fact_sales_id = ss.fact_sales_id
            left join return_flags rf    on rf.fact_sales_id = ss.fact_sales_id
        ),
        deleted as (
            delete from fact_finance ff
            using source_sales ss
            where ff.fact_sales_id = ss.fact_sales_id
        )
        insert into fact_finance (
            fact_sales_id,
            revenue_gross_amount,
            revenue_net_amount,
            payout_from_marketplace,
            mp_based_net,
            currency,
            is_refund,
            created_at,
            updated_at
        )
        select
            finalized.fact_sales_id,
            finalized.revenue_gross_amount,
            finalized.revenue_net_amount,
            finalized.payout_from_marketplace,
            finalized.mp_based_net,
            finalized.currency,
            finalized.is_refund,
            now(),
            now()
        from finalized
        """.formatted(platform, RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId);

    namedParameterJdbcTemplate.update(query, params);
  }

  @Override
  public void upsertFromOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String query = """
        with raw_postings as (
            select
                r.account_id                                   as account_id,
                nullif(r.payload::jsonb ->> 'posting_number','')
                                                              as order_id,
                nullif(item ->> 'sku','')                      as source_product_id,
                nullif(item ->> 'quantity','')::int            as quantity,
                nullif(item ->> 'price','')::numeric           as price,
                coalesce(nullif(item ->> 'currency_code',''), 'RUB')
                                                              as currency
            from %2$s r
            join lateral jsonb_array_elements(r.payload::jsonb -> 'products') item on true
            where r.account_id = :accountId
              and r.request_id = :requestId
            union all
            select
                r.account_id                                   as account_id,
                nullif(r.payload::jsonb ->> 'posting_number','')
                                                              as order_id,
                nullif(item ->> 'sku','')                      as source_product_id,
                nullif(item ->> 'quantity','')::int            as quantity,
                nullif(item ->> 'price','')::numeric           as price,
                coalesce(nullif(item ->> 'currency_code',''), 'RUB')
                                                              as currency
            from %3$s r
            join lateral jsonb_array_elements(r.payload::jsonb -> 'products') item on true
            where r.account_id = :accountId
              and r.request_id = :requestId
        ),
        source_sales as (
            select distinct
                fs.id              as fact_sales_id,
                fs.account_id      as account_id,
                fs.source_platform as source_platform,
                fs.order_id        as order_id,
                fs.dim_product_id  as dim_product_id,
                fs.quantity        as quantity,
                fs.sale_date       as sale_date,
                fs.warehouse_id    as warehouse_id,
                rp.quantity        as posting_quantity,
                rp.price           as posting_price,
                rp.currency        as posting_currency
            from fact_sales fs
            join dim_product dp
              on dp.id = fs.dim_product_id
            join raw_postings rp
              on rp.account_id = fs.account_id
             and rp.order_id = fs.order_id
             and rp.source_product_id = dp.source_product_id
            where fs.account_id = :accountId
              and fs.source_platform = '%1$s'
        ),
        posting_totals as (
            select
                account_id as account_id,
                order_id   as order_id,
                sum(coalesce(price, 0) * coalesce(quantity, 0))
                           as total_posting_price
            from raw_postings
            group by account_id, order_id
        ),
        finance_raw as (
            select
                r.account_id                                     as account_id,
                nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','')
                                                                  as order_id,
                sum(
                    coalesce(
                        nullif(r.payload::jsonb ->> 'accruals_for_sale','')::numeric,
                        0
                    )
                )                                                 as gross_total,
                sum(
                    case
                      when lower(
                               coalesce(
                                   nullif(r.payload::jsonb ->> 'type',''),
                                   ''
                               )
                           ) in ('orders', 'returns', 'compensation')
                        then coalesce(
                                 nullif(r.payload::jsonb ->> 'amount','')::numeric,
                                 0
                             )
                      else 0
                    end
                )                                                 as net_total,
                max(
                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_code',''),
                        'RUB'
                    )
                )                                                 as currency,
                bool_or(
                    lower(
                        coalesce(
                            nullif(r.payload::jsonb ->> 'type',''),
                            ''
                        )
                    ) = 'returns'
                )                                                 as is_refund_raw
            from %4$s r
            where r.account_id = :accountId
              and r.request_id = :requestId
              and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','') is not null
            group by r.account_id, order_id
        ),
        sales_finance as (
            select
                ss.fact_sales_id as fact_sales_id,
                coalesce(
                    fr.gross_total
                    * (coalesce(ss.posting_price, 0) * coalesce(ss.posting_quantity, 0))
                    / nullif(pt.total_posting_price, 0),
                    coalesce(ss.posting_price, 0) * coalesce(ss.posting_quantity, 0),
                    0
                )                               as revenue_gross_amount,
                coalesce(
                    fr.net_total
                    * (coalesce(ss.posting_price, 0) * coalesce(ss.posting_quantity, 0))
                    / nullif(pt.total_posting_price, 0),
                    0
                )                               as net_from_marketplace,
                coalesce(fr.currency, ss.posting_currency, 'RUB')
                                                 as currency,
                coalesce(fr.is_refund_raw, false)
                                                 as is_refund_raw
            from source_sales ss
            left join posting_totals pt
              on pt.account_id = ss.account_id
             and pt.order_id = ss.order_id
            left join finance_raw fr
              on fr.account_id = ss.account_id
             and fr.order_id = ss.order_id
        ),
        commission_costs as (
            select
                ss.fact_sales_id as fact_sales_id,
                sum(fc.amount)   as total_commission
            from source_sales ss
            left join fact_commission fc
              on fc.account_id = ss.account_id
             and fc.source_platform = ss.source_platform
             and fc.order_id = ss.order_id
            group by ss.fact_sales_id
        ),
        logistics_costs as (
            select
                ss.fact_sales_id as fact_sales_id,
                sum(fl.amount)   as total_logistics
            from source_sales ss
            left join fact_logistics_costs fl
              on fl.account_id      = ss.account_id
             and fl.source_platform = ss.source_platform
             and fl.operation_date  = ss.sale_date
             and fl.warehouse_id    = ss.warehouse_id
            group by ss.fact_sales_id
        ),
        product_costs as (
            select
                ss.fact_sales_id                  as fact_sales_id,
                sum(fpc.cost_value * ss.quantity) as total_product_cost
            from source_sales ss
            left join fact_product_cost fpc
              on fpc.account_id = ss.account_id
             and fpc.product_id = ss.dim_product_id
             and ss.sale_date >= fpc.valid_from
             and (fpc.valid_to is null or ss.sale_date <= fpc.valid_to)
            group by ss.fact_sales_id
        ),
        return_flags as (
            select
                ss.fact_sales_id           as fact_sales_id,
                bool_or(fr.id is not null) as has_returns
            from source_sales ss
            left join fact_returns fr
              on fr.account_id = ss.account_id
             and fr.source_platform = ss.source_platform
             and fr.order_id = ss.order_id
             and fr.dim_product_id = ss.dim_product_id
            group by ss.fact_sales_id
        ),
        finalized as (
            select
                ss.fact_sales_id                                            as fact_sales_id,
                coalesce(sf.revenue_gross_amount, 0)                         as revenue_gross_amount,
                (coalesce(sf.revenue_gross_amount, 0)
                    - coalesce(cc.total_commission, 0)
                    - coalesce(lc.total_logistics, 0)
                    - coalesce(pc.total_product_cost, 0)
                )                                                            as revenue_net_amount,
                coalesce(sf.net_from_marketplace, 0)                         as payout_from_marketplace,
                (coalesce(sf.net_from_marketplace, 0)
                    - coalesce(pc.total_product_cost, 0)
                )                                                            as mp_based_net,
                coalesce(sf.currency, 'RUB')                                 as currency,
                coalesce(sf.is_refund_raw, false) or coalesce(rf.has_returns, false)
                                                                             as is_refund
            from source_sales ss
            left join sales_finance   sf on sf.fact_sales_id = ss.fact_sales_id
            left join commission_costs cc on cc.fact_sales_id = ss.fact_sales_id
            left join logistics_costs lc  on lc.fact_sales_id = ss.fact_sales_id
            left join product_costs pc   on pc.fact_sales_id = ss.fact_sales_id
            left join return_flags rf    on rf.fact_sales_id = ss.fact_sales_id
        ),
        deleted as (
            delete from fact_finance ff
            using source_sales ss
            where ff.fact_sales_id = ss.fact_sales_id
        )
        insert into fact_finance (
            fact_sales_id,
            revenue_gross_amount,
            revenue_net_amount,
            payout_from_marketplace,
            mp_based_net,
            currency,
            is_refund,
            created_at,
            updated_at
        )
        select
            finalized.fact_sales_id,
            finalized.revenue_gross_amount,
            finalized.revenue_net_amount,
            finalized.payout_from_marketplace,
            finalized.mp_based_net,
            finalized.currency,
            finalized.is_refund,
            now(),
            now()
        from finalized
        """.formatted(
        platform,
        RawTableNames.RAW_OZON_POSTINGS_FBS,
        RawTableNames.RAW_OZON_POSTINGS_FBO,
        RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS
    );

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId);

    namedParameterJdbcTemplate.update(query, params);
  }
}
