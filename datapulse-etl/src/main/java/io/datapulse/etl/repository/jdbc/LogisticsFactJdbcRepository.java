package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.LogisticsFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LogisticsFactJdbcRepository implements LogisticsFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_logistics_costs (
          account_id,
          source_platform,
          operation_date,
          warehouse_id,
          order_id,
          dim_product_id,
          logistics_type,
          amount,
          currency,
          amount_raw,
          amount_abs,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          operation_date,
          warehouse_id,
          order_id,
          dim_product_id,
          logistics_type,
          amount,
          currency,
          amount_raw,
          amount_abs,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, operation_date, warehouse_id, logistics_type, order_id, dim_product_id)
      do update
      set
          amount     = excluded.amount,
          currency   = excluded.currency,
          amount_raw = excluded.amount_raw,
          amount_abs = excluded.amount_abs,
          updated_at = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        select
            account_id,
            source_platform,
            operation_date,
            warehouse_id,
            order_id,
            dim_product_id,
            logistics_type,
            amount,
            currency,
            amount_raw,
            amount_abs
        from (
            select
                s.account_id      as account_id,
                s.source_platform as source_platform,
                s.operation_date  as operation_date,
                s.warehouse_id    as warehouse_id,
                s.order_id        as order_id,
                s.dim_product_id  as dim_product_id,
                s.logistics_type  as logistics_type,
                sum(s.amount)     as amount,
                max(s.currency)   as currency,
                sum(s.amount_raw) as amount_raw,
                sum(s.amount_abs) as amount_abs
            from (
                select
                    r.account_id                                      as account_id,
                    '%1$s'                                            as source_platform,
                    (r.payload::jsonb ->> 'rr_dt')::date             as operation_date,
                    w.id                                             as warehouse_id,
                    nullif(r.payload::jsonb ->> 'srid','')           as order_id,
                    dp.id                                            as dim_product_id,
                    l.logistics_type                                 as logistics_type,
                    abs(l.amount)                                    as amount,
                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_name',''),
                        'RUB'
                    )                                                as currency,
                    l.amount                                         as amount_raw,
                    abs(l.amount)                                    as amount_abs
                from %2$s r
                left join dim_warehouse w
                  on w.account_id = r.account_id
                 and lower(w.source_platform) = lower('%1$s')
                 and w.external_warehouse_id =
                     nullif(r.payload::jsonb ->> 'ppvz_office_id','')
                join dim_product dp
                  on dp.account_id        = r.account_id
                 and dp.source_platform   = '%1$s'
                 and dp.source_product_id = nullif(r.payload::jsonb ->> 'nm_id','')
                left join fact_returns fr
                  on fr.account_id      = r.account_id
                 and fr.source_platform = '%1$s'
                 and fr.order_id        = nullif(r.payload::jsonb ->> 'srid','')
                 and fr.dim_product_id  = dp.id
                join lateral (
                    values
                      ('DELIVERY_TO_CUSTOMER',
                       nullif(r.payload::jsonb ->> 'delivery_rub','')::numeric),
                      ('RETURN_FROM_CUSTOMER',
                       nullif(r.payload::jsonb ->> 'return_amount','')::numeric),
                      ('INBOUND',
                       nullif(r.payload::jsonb ->> 'rebill_logistic_cost','')::numeric),
                      ('STORAGE',
                       nullif(r.payload::jsonb ->> 'storage_fee','')::numeric),
                      ('INBOUND',
                       nullif(r.payload::jsonb ->> 'acceptance','')::numeric)
                ) as l(logistics_type, amount)
                  on l.amount is not null and l.amount <> 0
                where r.account_id = ?
                  and r.request_id = ?
                  and nullif(r.payload::jsonb ->> 'rr_dt','') is not null
                  and nullif(r.payload::jsonb ->> 'srid','')  is not null
                  and dp.id is not null
                  and (
                    l.logistics_type <> 'RETURN_FROM_CUSTOMER'
                    or fr.id is not null
                  )
            ) s
            group by
                s.account_id,
                s.source_platform,
                s.operation_date,
                s.warehouse_id,
                s.order_id,
                s.dim_product_id,
                s.logistics_type
        ) t
        """.formatted(platform, RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        select
            account_id,
            source_platform,
            operation_date,
            warehouse_id,
            order_id,
            dim_product_id,
            logistics_type,
            amount,
            currency,
            amount_raw,
            amount_abs
        from (
            select
                s.account_id      as account_id,
                s.source_platform as source_platform,
                s.operation_date  as operation_date,
                s.warehouse_id    as warehouse_id,
                s.order_id        as order_id,
                s.dim_product_id  as dim_product_id,
                s.logistics_type  as logistics_type,
                sum(s.amount)     as amount,
                max(s.currency)   as currency,
                sum(s.amount_raw) as amount_raw,
                sum(s.amount_abs) as amount_abs
            from (
                select
                    r.account_id                                     as account_id,
                    '%1$s'                                           as source_platform,
                    (r.payload::jsonb ->> 'operation_date')::timestamptz::date
                                                                    as operation_date,
                    w.id                                            as warehouse_id,
                    nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','')
                                                                    as order_id,
                    dp.id                                           as dim_product_id,
                    case
                      when ot in (
                          'OperationAgentDeliveredToCustomer',
                          'OperationAgentDeliveredToCustomerCanceled',
                          'OperationAgentStornoDeliveredToCustomer',
                          'MarketplaceServiceItemDirectFlowLogistic',
                          'MarketplaceServiceItemDirectFlowLogisticVDC',
                          'MarketplaceServiceItemDeliveryKGT'
                      ) then 'DELIVERY_TO_CUSTOMER'
                      when ot in (
                          'OperationReturnGoodsFBSofRMS',
                          'OperationItemReturn',
                          'MarketplaceServiceItemReturnFlowLogistic'
                      ) then 'RETURN_FROM_CUSTOMER'
                      when ot in (
                          'OperationMarketplaceCrossDockServiceWriteOff',
                          'MarketplaceServiceItemRedistributionReturnsPVZ',
                          'MarketplaceServiceItemDropoffPPZ'
                      ) then 'INBOUND'
                      when ot in (
                          'OperationMarketplaceServiceStorage',
                          'MarketplaceReturnStorageServiceAtThePickupPointFbsItem',
                          'MarketplaceReturnStorageServiceInTheWarehouseFbsItem'
                      ) then 'STORAGE'
                      else null
                    end                                              as logistics_type,
                    abs(nullif(r.payload::jsonb ->> 'amount','')::numeric)
                                                                    as amount,
                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_code',''),
                        'RUB'
                    )                                               as currency,
                    nullif(r.payload::jsonb ->> 'amount','')::numeric
                                                                    as amount_raw,
                    abs(nullif(r.payload::jsonb ->> 'amount','')::numeric)
                                                                    as amount_abs
                from %2$s r
                join dim_warehouse w
                  on w.account_id = r.account_id
                 and lower(w.source_platform) = lower('%1$s')
                 and w.external_warehouse_id =
                     nullif(r.payload::jsonb -> 'posting' ->> 'warehouse_id','')
                join lateral jsonb_array_elements(r.payload::jsonb -> 'items') item
                  on true
                left join dim_product dp
                  on dp.account_id        = r.account_id
                 and dp.source_platform   = '%1$s'
                 and dp.source_product_id = nullif(item ->> 'sku','')
                left join fact_returns fr
                  on fr.account_id      = r.account_id
                 and fr.source_platform = '%1$s'
                 and fr.order_id        = nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','')
                 and fr.dim_product_id  = dp.id
                cross join lateral (
                   values (r.payload::jsonb ->> 'operation_type')
                ) ot(ot)
                where r.account_id = ?
                  and r.request_id = ?
                  and nullif(r.payload::jsonb ->> 'operation_date','') is not null
                  and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','') is not null
                  and nullif(r.payload::jsonb ->> 'amount','')         is not null
                  and nullif(item ->> 'sku','')                         is not null
                  and dp.id is not null
                  and (
                    ot not in (
                        'OperationReturnGoodsFBSofRMS',
                        'OperationItemReturn',
                        'MarketplaceServiceItemReturnFlowLogistic'
                    )
                    or fr.id is not null
                  )
              ) s
            where s.logistics_type is not null
              and s.amount is not null
            group by
                s.account_id,
                s.source_platform,
                s.operation_date,
                s.warehouse_id,
                s.order_id,
                s.dim_product_id,
                s.logistics_type
        ) t
        """.formatted(platform, RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
