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
          order_id,
          operation_date,
          warehouse_id,
          logistics_type,
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
          warehouse_id,
          logistics_type,
          amount,
          currency,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, operation_date, warehouse_id, logistics_type)
      do update
      set
          order_id   = excluded.order_id,
          amount     = excluded.amount,
          currency   = excluded.currency,
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
            order_id,
            operation_date,
            warehouse_id,
            logistics_type,
            amount,
            currency
        from (
            select
                s.account_id        as account_id,
                s.source_platform   as source_platform,
                min(s.order_id)     as order_id,
                s.operation_date    as operation_date,
                s.warehouse_id      as warehouse_id,
                s.logistics_type    as logistics_type,
                sum(s.amount)       as amount,
                max(s.currency)     as currency
            from (
                select
                    r.account_id                                       as account_id,
                    '%1$s'                                             as source_platform,
                    nullif(r.payload::jsonb ->> 'srid','')            as order_id,
                    (r.payload::jsonb ->> 'rr_dt')::date              as operation_date,
                    w.id                                              as warehouse_id,
                    l.logistics_type                                  as logistics_type,
                    l.amount                                          as amount,
                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_name',''),
                        'RUB'
                    )                                                 as currency
                from %2$s r
                left join dim_warehouse w
                  on w.account_id = r.account_id
                 and lower(w.source_platform) = lower('%1$s')
                 and w.external_warehouse_id =
                     nullif(r.payload::jsonb ->> 'ppvz_office_id','')
                join lateral (
                    values
                      ('DELIVERY_TO_CUSTOMER', nullif(r.payload::jsonb ->> 'delivery_amount','')::numeric),
                      ('RETURN_FROM_CUSTOMER', nullif(r.payload::jsonb ->> 'return_amount','')::numeric),
                      ('INBOUND', nullif(r.payload::jsonb ->> 'rebill_logistic_cost','')::numeric),
                      ('STORAGE', nullif(r.payload::jsonb ->> 'storage_fee','')::numeric),
                      ('INBOUND', nullif(r.payload::jsonb ->> 'acceptance','')::numeric)
                ) as l(logistics_type, amount)
                  on l.amount is not null and l.amount <> 0
                where r.account_id = ?
                  and r.request_id = ?
                  and nullif(r.payload::jsonb ->> 'rr_dt','') is not null
                  and nullif(r.payload::jsonb ->> 'srid','')  is not null
            ) s
            group by
                s.account_id,
                s.source_platform,
                s.operation_date,
                s.warehouse_id,
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
            order_id,
            operation_date,
            warehouse_id,
            logistics_type,
            amount,
            currency
        from (
            select
                s.account_id        as account_id,
                s.source_platform   as source_platform,
                min(s.order_id)     as order_id,
                s.operation_date    as operation_date,
                s.warehouse_id      as warehouse_id,
                s.logistics_type    as logistics_type,
                sum(s.amount)       as amount,
                max(s.currency)     as currency
            from (
                select
                    r.account_id                                     as account_id,
                    '%1$s'                                           as source_platform,
                    nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','') as order_id,
                    (r.payload::jsonb ->> 'operation_date')::timestamptz::date    as operation_date,
                    w.id                                            as warehouse_id,
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
                    case
                      when nullif(r.payload::jsonb ->> 'amount','')::numeric < 0
                        then - nullif(r.payload::jsonb ->> 'amount','')::numeric
                      else null
                    end                                              as amount,
                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_code',''),
                        'RUB'
                    )                                               as currency
                from %2$s r
                left join dim_warehouse w
                  on w.account_id = r.account_id
                 and lower(w.source_platform) = lower('%1$s')
                 and w.external_warehouse_id =
                     nullif(r.payload::jsonb -> 'posting' ->> 'warehouse_id','')
                cross join lateral (
                   values (r.payload::jsonb ->> 'operation_type')
                ) ot(ot)
                where r.account_id = ?
                  and r.request_id = ?
                  and nullif(r.payload::jsonb ->> 'operation_date','') is not null
                  and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number','') is not null
                  and nullif(r.payload::jsonb ->> 'amount','')         is not null
              ) s
            where s.logistics_type is not null
              and s.amount is not null
            group by
                s.account_id,
                s.source_platform,
                s.operation_date,
                s.warehouse_id,
                s.logistics_type
        ) t
        """.formatted(platform, RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
