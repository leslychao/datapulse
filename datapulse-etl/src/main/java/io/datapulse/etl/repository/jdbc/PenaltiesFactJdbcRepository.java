package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.PenaltiesFactRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PenaltiesFactJdbcRepository implements PenaltiesFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_penalties (
          account_id,
          source_platform,
          order_id,
          penalty_date,
          penalty_type,
          penalty_source_code,
          amount,
          currency,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          order_id,
          penalty_date,
          penalty_type,
          penalty_source_code,
          amount,
          currency,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, penalty_date, order_id, penalty_type)
      do update
      set
          penalty_source_code = excluded.penalty_source_code,
          amount              = excluded.amount,
          currency            = excluded.currency,
          updated_at          = now();
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
            penalty_date,
            penalty_type,
            penalty_source_code,
            sum(amount) as amount,
            currency
        from (
            select
                r.account_id                                                             as account_id,
                '%1$s'                                                                   as source_platform,
                nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '')          as order_id,
                (r.payload::jsonb ->> 'operation_date')::timestamptz::date              as penalty_date,
                case
                  when x.operation_type = 'OperationMarketplaceWithHoldingForUndeliverableGoods'
                    then 'UNDERDELIVERY_PENALTY'
                  when x.type_raw = 'services'
                    then 'SERVICE_FEE'
                  when x.type_raw = 'other'
                    then 'OTHER_WITHHOLDING'
                  else 'UNCLASSIFIED_WITHHOLDING'
                end                                                                      as penalty_type,
                coalesce(
                    nullif(r.payload::jsonb ->> 'operation_type_name', ''),
                    nullif(r.payload::jsonb ->> 'operation_type', ''),
                    'UNKNOWN'
                )                                                                        as penalty_source_code,
                abs(x.amount_raw)                                                        as amount,
                coalesce(
                    nullif(r.payload::jsonb ->> 'currency_code', ''),
                    'RUB'
                )                                                                        as currency
            from %2$s r
            cross join lateral (
               select
                   (r.payload::jsonb ->> 'operation_type')                               as operation_type,
                   nullif(r.payload::jsonb ->> 'amount', '')::numeric                   as amount_raw,
                   nullif(r.payload::jsonb ->> 'type', '')                               as type_raw
            ) x
            where r.account_id = ?
              and r.request_id = ?
              -- дата операции обязательна
              and nullif(r.payload::jsonb ->> 'operation_date', '') is not null
              -- должен быть заказ (posting_number)
              and nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '') is not null
              -- отрицательная сумма = удержание
              and x.amount_raw is not null
              and x.amount_raw <> 0
              and x.amount_raw < 0
              -- не комиссионные за продажу (они в fact_commission)
              and coalesce(
                    nullif(r.payload::jsonb ->> 'sale_commission', '')::numeric,
                    0
                  ) = 0
              -- не чистая логистика/склад (они в fact_logistics_costs и т.п.)
              and x.operation_type not in (
                  'OperationAgentDeliveredToCustomer',
                  'OperationAgentDeliveredToCustomerCanceled',
                  'OperationAgentStornoDeliveredToCustomer',
                  'MarketplaceServiceItemDirectFlowLogistic',
                  'MarketplaceServiceItemDirectFlowLogisticVDC',
                  'MarketplaceServiceItemDeliveryKGT',
                  'OperationReturnGoodsFBSofRMS',
                  'OperationItemReturn',
                  'MarketplaceServiceItemReturnFlowLogistic',
                  'OperationMarketplaceCrossDockServiceWriteOff',
                  'MarketplaceServiceItemRedistributionReturnsPVZ',
                  'MarketplaceServiceItemDropoffPPZ',
                  'OperationMarketplaceServiceStorage',
                  'MarketplaceReturnStorageServiceAtThePickupPointFbsItem',
                  'MarketplaceReturnStorageServiceInTheWarehouseFbsItem'
              )
        ) s
        where order_id is not null
        group by
            account_id,
            source_platform,
            order_id,
            penalty_date,
            penalty_type,
            penalty_source_code,
            currency
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
            penalty_date,
            penalty_type,
            penalty_source_code,
            sum(amount) as amount,
            currency
        from (
            select
                r.account_id                                                as account_id,
                '%1$s'                                                      as source_platform,
                nullif(r.payload::jsonb ->> 'srid', '')                     as order_id,
                (r.payload::jsonb ->> 'rr_dt')::timestamptz::date           as penalty_date,
                case
                  when coalesce(nullif(r.payload::jsonb ->> 'bonus_type_name', ''), '')
                       ilike 'штраф%%'
                    then 'ORDER_PENALTY'
                  else 'OTHER_PENALTY'
                end                                                         as penalty_type,
                coalesce(
                    nullif(r.payload::jsonb ->> 'bonus_type_name', ''),
                    'WB_PENALTY'
                )                                                           as penalty_source_code,
                abs(nullif(r.payload::jsonb ->> 'penalty', '')::numeric)    as amount,
                coalesce(
                    nullif(r.payload::jsonb ->> 'currency_name', ''),
                    'RUB'
                )                                                           as currency
            from %2$s r
            where r.account_id = ?
              and r.request_id = ?
              and nullif(r.payload::jsonb ->> 'rr_dt', '') is not null
              and nullif(r.payload::jsonb ->> 'srid', '')  is not null
              and nullif(r.payload::jsonb ->> 'penalty', '') is not null
              and nullif(r.payload::jsonb ->> 'penalty', '') <> '0'
        ) s
        where amount <> 0
        group by
            account_id,
            source_platform,
            order_id,
            penalty_date,
            penalty_type,
            penalty_source_code,
            currency
        """.formatted(
        platform,
        RawTableNames.RAW_WB_SALES_REPORT_DETAIL
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
