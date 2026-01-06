package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.LogisticsFactRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LogisticsFactJdbcRepository implements LogisticsFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_logistics_costs (
          account_id,
          source_platform,
          source_event_id,
          order_id,
          warehouse_id,
          operation_date,
          logistics_type,
          logistics_charge_amount,
          logistics_refund_amount,
          currency,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          order_id,
          warehouse_id,
          operation_date,
          logistics_type,
          logistics_charge_amount,
          logistics_refund_amount,
          currency,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, source_event_id)
      do update
      set
          order_id                = excluded.order_id,
          warehouse_id            = excluded.warehouse_id,
          operation_date          = excluded.operation_date,
          logistics_type          = excluded.logistics_type,
          logistics_charge_amount = excluded.logistics_charge_amount,
          logistics_refund_amount = excluded.logistics_refund_amount,
          currency                = excluded.currency,
          updated_at              = now()
      """;

  private static final String OZON_LOGISTICS_SELECT = """
      select
          :accountId                                             as account_id,
          :sourcePlatform                                        as source_platform,
          r.payload::jsonb ->> 'operation_id'                    as source_event_id,
          nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '') as order_id,
          dw.id                                                  as warehouse_id,
          (r.payload::jsonb ->> 'operation_date')::timestamptz::date as operation_date,
          case
            when r.payload::jsonb ->> 'operation_type' in (
              'OperationAgentDeliveredToCustomer',
              'OperationAgentDeliveredToCustomerCanceled',
              'OperationAgentStornoDeliveredToCustomer',
              'OperationMarketplaceCrossDockServiceWriteOff',
              'MarketplaceServiceItemDirectFlowLogistic',
              'MarketplaceServiceItemDeliveryKGT',
              'MarketplaceServiceItemDirectFlowLogisticVDC',
              'ItemAdvertisementForSupplierLogistic',
              'ItemAdvertisementForSupplierLogisticSeller',
              'MarketplaceServiceItemDropoffPPZ',
              'MarketplaceServiceItemDropoffFF',
              'MarketplaceServiceItemDropoffPVZ',
              'MarketplaceServiceItemDropoffSC',
              'MarketplaceServiceItemPickup',
              'MarketplaceServiceItemFulfillment',
              'MarketplaceServiceItemDelivToCustomer',
              'MarketplaceServiceItemDirectFlowTrans'
            ) then 'OUTBOUND'
            when r.payload::jsonb ->> 'operation_type' in (
              'OperationItemReturn',
              'OperationReturnGoodsFBSofRMS',
              'MarketplaceServiceItemReturnFlowLogistic',
              'MarketplaceServiceItemReturnAfterDelivToCustomer',
              'MarketplaceServiceItemReturnNotDelivToCustomer',
              'MarketplaceServiceItemReturnPartGoodsCustomer',
              'MarketplaceServiceItemReturnFlowTrans',
              'MarketplaceReturnStorageServiceAtThePickupPointFbsItem',
              'MarketplaceReturnStorageServiceInTheWarehouseFbsItem',
              'MarketplaceServiceItemRedistributionReturnsPVZ'
            ) then 'RETURN'
            when r.payload::jsonb ->> 'operation_type' in (
              'OperationMarketplaceServiceStorage'
            ) then 'STORAGE'
          end                                                    as logistics_type,
          case
            when ln.amount_num < 0 then -ln.amount_num
            else 0
          end                                                    as logistics_charge_amount,
          case
            when ln.amount_num > 0 then ln.amount_num
            else 0
          end                                                    as logistics_refund_amount,
          'RUB'                                                  as currency
      from %s r
      cross join lateral (
          select nullif(r.payload::jsonb ->> 'amount', '')::numeric as amount_num
      ) ln
      left join dim_warehouse dw
        on dw.account_id            = r.account_id
       and dw.source_platform       = :sourcePlatform
       and dw.external_warehouse_id = nullif(r.payload::jsonb -> 'posting' ->> 'warehouse_id', '')
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.marketplace = 'OZON'
        and r.payload::jsonb ->> 'operation_type' in (
          'OperationAgentDeliveredToCustomer',
          'OperationAgentDeliveredToCustomerCanceled',
          'OperationAgentStornoDeliveredToCustomer',
          'OperationMarketplaceCrossDockServiceWriteOff',
          'OperationItemReturn',
          'OperationReturnGoodsFBSofRMS',
          'OperationMarketplaceServiceStorage',
          'MarketplaceReturnStorageServiceAtThePickupPointFbsItem',
          'MarketplaceReturnStorageServiceInTheWarehouseFbsItem',
          'MarketplaceServiceItemDirectFlowLogistic',
          'MarketplaceServiceItemReturnFlowLogistic',
          'MarketplaceServiceItemDeliveryKGT',
          'MarketplaceServiceItemDirectFlowLogisticVDC',
          'ItemAdvertisementForSupplierLogistic',
          'ItemAdvertisementForSupplierLogisticSeller',
          'MarketplaceServiceItemDropoffPPZ',
          'MarketplaceServiceItemDropoffFF',
          'MarketplaceServiceItemDropoffPVZ',
          'MarketplaceServiceItemDropoffSC',
          'MarketplaceServiceItemPickup',
          'MarketplaceServiceItemFulfillment',
          'MarketplaceServiceItemDelivToCustomer',
          'MarketplaceServiceItemDirectFlowTrans',
          'MarketplaceServiceItemReturnAfterDelivToCustomer',
          'MarketplaceServiceItemReturnNotDelivToCustomer',
          'MarketplaceServiceItemReturnPartGoodsCustomer',
          'MarketplaceServiceItemReturnFlowTrans',
          'MarketplaceServiceItemRedistributionReturnsPVZ'
        )
        and ln.amount_num is not null
        and ln.amount_num <> 0
      """.formatted(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

  private static final String WILDBERRIES_LOGISTICS_SELECT = """
      select
          :accountId                                                                as account_id,
          :sourcePlatform                                                           as source_platform,
          concat(base.realizationreport_id, '-', base.rrd_id, '-', base.logistics_kind)
                                                                                    as source_event_id,
          base.srid                                                                 as order_id,
          dw.id                                                                     as warehouse_id,
          base.rr_dt_date                                                           as operation_date,
          base.logistics_kind                                                       as logistics_type,
          sum(case when base.amount > 0 then base.amount else 0 end)               as logistics_charge_amount,
          sum(case when base.amount < 0 then -base.amount else 0 end)              as logistics_refund_amount,
          'RUB'                                                                     as currency
      from (
          select
              r.payload::jsonb ->> 'realizationreport_id'                           as realizationreport_id,
              r.payload::jsonb ->> 'rrd_id'                                         as rrd_id,
              r.payload::jsonb ->> 'srid'                                           as srid,
              nullif(r.payload::jsonb ->> 'ppvz_office_id', '')::bigint            as ppvz_office_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date                    as rr_dt_date,
              'OUTBOUND'::text                                                     as logistics_kind,
              (
                coalesce(nullif(r.payload::jsonb ->> 'delivery_rub', '')::numeric, 0)
                + coalesce(nullif(r.payload::jsonb ->> 'rebill_logistic_cost', '')::numeric, 0)
                + coalesce(nullif(r.payload::jsonb ->> 'acceptance', '')::numeric, 0)
                + coalesce(nullif(r.payload::jsonb ->> 'deduction', '')::numeric, 0)
              )                                                                     as amount
          from %1$s r
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and (
              coalesce(nullif(r.payload::jsonb ->> 'delivery_rub', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'rebill_logistic_cost', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'acceptance', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'deduction', '')::numeric, 0)
            ) <> 0
            and coalesce(nullif(r.payload::jsonb ->> 'return_amount', '')::numeric, 0) = 0
            and coalesce(r.payload::jsonb ->> 'doc_type_name', '') <> 'Возврат'
            and coalesce(nullif(r.payload::jsonb ->> 'quantity', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_price', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_amount', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_price_withdisc_rub', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'ppvz_for_pay', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'ppvz_sales_commission', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'penalty', '')::numeric, 0) = 0

          union all

          select
              r.payload::jsonb ->> 'realizationreport_id'                           as realizationreport_id,
              r.payload::jsonb ->> 'rrd_id'                                         as rrd_id,
              r.payload::jsonb ->> 'srid'                                           as srid,
              nullif(r.payload::jsonb ->> 'ppvz_office_id', '')::bigint            as ppvz_office_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date                    as rr_dt_date,
              'RETURN'::text                                                       as logistics_kind,
              (
                coalesce(nullif(r.payload::jsonb ->> 'delivery_rub', '')::numeric, 0)
                + coalesce(nullif(r.payload::jsonb ->> 'rebill_logistic_cost', '')::numeric, 0)
                + coalesce(nullif(r.payload::jsonb ->> 'acceptance', '')::numeric, 0)
                + coalesce(nullif(r.payload::jsonb ->> 'deduction', '')::numeric, 0)
              )                                                                     as amount
          from %1$s r
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and (
              coalesce(nullif(r.payload::jsonb ->> 'delivery_rub', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'rebill_logistic_cost', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'acceptance', '')::numeric, 0)
              + coalesce(nullif(r.payload::jsonb ->> 'deduction', '')::numeric, 0)
            ) <> 0
            and (
              coalesce(nullif(r.payload::jsonb ->> 'return_amount', '')::numeric, 0) <> 0
              or coalesce(r.payload::jsonb ->> 'doc_type_name', '') = 'Возврат'
            )
            and coalesce(nullif(r.payload::jsonb ->> 'quantity', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_price', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_amount', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_price_withdisc_rub', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'ppvz_for_pay', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'ppvz_sales_commission', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'penalty', '')::numeric, 0) = 0

          union all

          select
              r.payload::jsonb ->> 'realizationreport_id'                           as realizationreport_id,
              r.payload::jsonb ->> 'rrd_id'                                         as rrd_id,
              r.payload::jsonb ->> 'srid'                                           as srid,
              nullif(r.payload::jsonb ->> 'ppvz_office_id', '')::bigint            as ppvz_office_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date                    as rr_dt_date,
              'STORAGE'::text                                                      as logistics_kind,
              coalesce(nullif(r.payload::jsonb ->> 'storage_fee', '')::numeric, 0) as amount
          from %1$s r
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and coalesce(nullif(r.payload::jsonb ->> 'storage_fee', '')::numeric, 0) <> 0
            and coalesce(nullif(r.payload::jsonb ->> 'quantity', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_price', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_amount', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'retail_price_withdisc_rub', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'ppvz_for_pay', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'ppvz_sales_commission', '')::numeric, 0) = 0
            and coalesce(nullif(r.payload::jsonb ->> 'penalty', '')::numeric, 0) = 0
      ) as base
      left join dim_warehouse dw
        on dw.account_id            = :accountId
       and dw.source_platform       = :sourcePlatform
       and dw.external_warehouse_id = base.ppvz_office_id::text
      group by
          base.realizationreport_id,
          base.rrd_id,
          base.logistics_kind,
          base.srid,
          dw.id,
          base.rr_dt_date
      """.formatted(RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(long accountId, String requestId) {
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS)) {
      selects.add(OZON_LOGISTICS_SELECT);
    }
    executeUpsert(selects, accountId, requestId, MarketplaceType.OZON.tag());
  }

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_WB_SALES_REPORT_DETAIL)) {
      selects.add(WILDBERRIES_LOGISTICS_SELECT);
    }
    executeUpsert(selects, accountId, requestId, MarketplaceType.WILDBERRIES.tag());
  }

  private void executeUpsert(List<String> selects, long accountId, String requestId,
      String sourcePlatform) {
    if (selects.isEmpty()) {
      return;
    }
    String unionSelect = String.join(" union all ", selects);
    String sql = UPSERT_TEMPLATE.formatted(unionSelect);
    MapSqlParameterSource parameters = new MapSqlParameterSource()
        .addValue("accountId", accountId)
        .addValue("requestId", requestId)
        .addValue("sourcePlatform", sourcePlatform);
    jdbcTemplate.update(sql, parameters);
  }

  private boolean tableExists(String tableName) {
    String sql = """
        select exists (
          select 1
          from information_schema.tables
          where table_schema = current_schema()
            and table_name = :tableName
        )
        """;
    MapSqlParameterSource parameters = new MapSqlParameterSource()
        .addValue("tableName", tableName);
    Boolean exists = jdbcTemplate.queryForObject(sql, parameters, Boolean.class);
    return Boolean.TRUE.equals(exists);
  }
}
