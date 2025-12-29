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

  /**
   * Идемпотентный upsert без ON CONFLICT:
   *
   * <ol>
   *   <li>source as (%s) — считаем grain:
   *       account × platform × date × warehouse × logistics_type</li>
   *   <li>delete ... using ... — удаляем старые строки с тем же grain
   *       (учитывая NULL в warehouse_id через IS NOT DISTINCT FROM)</li>
   *   <li>insert ... select ... — вставляем новые агрегаты</li>
   * </ol>
   */
  private static final String UPSERT_TEMPLATE = """
      with source as (
          %s
      ),
      deleted as (
          delete from fact_logistics_costs f
          using source s
          where f.account_id      = s.account_id
            and f.source_platform = s.source_platform
            and f.operation_date  = s.operation_date
            and f.logistics_type  = s.logistics_type
            and f.warehouse_id is not distinct from s.warehouse_id
      )
      insert into fact_logistics_costs (
          account_id,
          source_platform,
          source_event_id,
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
          source_event_id,
          operation_date,
          warehouse_id,
          logistics_type,
          amount,
          currency,
          now(),
          now()
      from source;
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
            source_event_id,
            operation_date,
            warehouse_id,
            logistics_type,
            amount,
            currency
        from (
            select
                s.account_id           as account_id,
                s.source_platform      as source_platform,
                min(s.source_event_id) as source_event_id,
                s.operation_date       as operation_date,
                s.warehouse_id         as warehouse_id,
                s.logistics_type       as logistics_type,
                sum(s.amount)          as amount,
                max(s.currency)        as currency
            from (
                select
                    r.account_id                                       as account_id,
                    '%1$s'                                             as source_platform,

                    -- гарантируем not null для source_event_id
                    coalesce(
                        nullif(r.payload::jsonb ->> 'order_uid',''),
                        nullif(r.payload::jsonb ->> 'srid',''),
                        concat_ws(
                            ':',
                            nullif(r.payload::jsonb ->> 'realizationreport_id',''),
                            nullif((r.payload::jsonb ->> 'rrd_id'),'')
                        )
                    )                                                 as source_event_id,

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
                      (
                        'DELIVERY_TO_CUSTOMER',
                        nullif(r.payload::jsonb ->> 'delivery_amount','')::numeric
                      ),
                      (
                        'RETURN_FROM_CUSTOMER',
                        nullif(r.payload::jsonb ->> 'return_amount','')::numeric
                      ),
                      (
                        'INBOUND',
                        nullif(r.payload::jsonb ->> 'rebill_logistic_cost','')::numeric
                      ),
                      (
                        'STORAGE',
                        nullif(r.payload::jsonb ->> 'storage_fee','')::numeric
                      ),
                      (
                        'INBOUND',
                        nullif(r.payload::jsonb ->> 'acceptance','')::numeric
                      )
                ) as l(logistics_type, amount)
                  on l.amount is not null and l.amount <> 0

                where r.account_id = ?
                  and r.request_id = ?
                  and nullif(r.payload::jsonb ->> 'rr_dt','') is not null
                  and (
                        nullif(r.payload::jsonb ->> 'order_uid','') is not null
                        or nullif(r.payload::jsonb ->> 'srid','') is not null
                        or nullif(r.payload::jsonb ->> 'realizationreport_id','') is not null
                     )
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
            source_event_id,
            operation_date,
            warehouse_id,
            logistics_type,
            amount,
            currency
        from (
            select
                s.account_id           as account_id,
                s.source_platform      as source_platform,
                min(s.source_event_id) as source_event_id,
                s.operation_date       as operation_date,
                s.warehouse_id         as warehouse_id,
                s.logistics_type       as logistics_type,
                sum(s.amount)          as amount,
                max(s.currency)        as currency
            from (
                select
                    r.account_id                                     as account_id,
                    '%1$s'                                           as source_platform,

                    nullif(r.payload::jsonb ->> 'operation_id','')  as source_event_id,

                    (r.payload::jsonb ->> 'operation_date')::timestamptz::date
                                                                    as operation_date,

                    w.id                                            as warehouse_id,

                    case
                      -- доставка к покупателю (стоимость доставки заказа)
                      when ot in (
                          'OperationAgentDeliveredToCustomer',
                          'OperationAgentDeliveredToCustomerCanceled',
                          'OperationAgentStornoDeliveredToCustomer',
                          'MarketplaceServiceItemDirectFlowLogistic',
                          'MarketplaceServiceItemDirectFlowLogisticVDC',
                          'MarketplaceServiceItemDeliveryKGT'
                      ) then 'DELIVERY_TO_CUSTOMER'

                      -- обратная логистика (возврат/невыкуп)
                      when ot in (
                          'OperationReturnGoodsFBSofRMS',
                          'OperationItemReturn',
                          'MarketplaceServiceItemReturnFlowLogistic'
                      ) then 'RETURN_FROM_CUSTOMER'

                      -- входящая логистика (на склад / кросс-докинг / dropoff)
                      when ot in (
                          'OperationMarketplaceCrossDockServiceWriteOff',
                          'MarketplaceServiceItemRedistributionReturnsPVZ',
                          'MarketplaceServiceItemDropoffPPZ'
                      ) then 'INBOUND'

                      -- хранение
                      when ot in (
                          'OperationMarketplaceServiceStorage',
                          'MarketplaceReturnStorageServiceAtThePickupPointFbsItem',
                          'MarketplaceReturnStorageServiceInTheWarehouseFbsItem'
                      ) then 'STORAGE'

                      else null
                    end                                              as logistics_type,

                    -- сырое движение по расчётам
                    nullif(r.payload::jsonb ->> 'amount','')::numeric
                                                                    as raw_amount,

                    -- нормализованный расход на логистику: только минусы, по модулю
                    case
                      when nullif(r.payload::jsonb ->> 'amount','')::numeric < 0
                        then - nullif(r.payload::jsonb ->> 'amount','')::numeric
                      else null
                    end                                              as amount,

                    coalesce(
                        nullif(r.payload::jsonb ->> 'currency_code',''),
                        'RUB'
                    )                                               as currency,

                    r.created_at                                    as created_at
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
                  and nullif(r.payload::jsonb ->> 'operation_id','')   is not null
                  and nullif(r.payload::jsonb ->> 'amount','')         is not null
              ) s
            where s.logistics_type is not null     -- только логистические операции
              and s.amount is not null             -- только реальные расходы (amount < 0 в RAW)
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
