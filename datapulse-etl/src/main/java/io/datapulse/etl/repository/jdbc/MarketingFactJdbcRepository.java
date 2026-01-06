package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.MarketingFactRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class MarketingFactJdbcRepository implements MarketingFactRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into fact_marketing_costs (
          account_id,
          source_platform,
          source_event_id,
          order_id,
          operation_date,
          marketing_type,
          marketing_charge_amount,
          marketing_refund_amount,
          currency,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          order_id,
          operation_date,
          marketing_type,
          marketing_charge_amount,
          marketing_refund_amount,
          currency,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, source_event_id, marketing_type)
      do update
      set
          order_id                = excluded.order_id,
          operation_date          = excluded.operation_date,
          marketing_charge_amount = excluded.marketing_charge_amount,
          marketing_refund_amount = excluded.marketing_refund_amount,
          currency                = excluded.currency,
          updated_at              = now()
      """;

  private static final String OZON_MARKETING_SELECT = """
      select
          :accountId      as account_id,
          :sourcePlatform as source_platform,
          t.source_event_id,
          t.order_id,
          t.operation_date,
          t.marketing_type,
          t.marketing_charge_amount,
          t.marketing_refund_amount,
          t.currency
      from (
          select
              r.payload::jsonb ->> 'operation_id'                            as source_event_id,
              nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '') as order_id,
              (r.payload::jsonb ->> 'operation_date')::timestamptz::date     as operation_date,
              r.payload::jsonb ->> 'operation_type'                          as marketing_type,
              case
                when amt.amount_num < 0 then -amt.amount_num
                else 0
              end                                                             as marketing_charge_amount,
              case
                when amt.amount_num > 0 then amt.amount_num
                else 0
              end                                                             as marketing_refund_amount,
              'RUB'                                                           as currency
          from %s r
          cross join lateral (
              select nullif(r.payload::jsonb ->> 'amount', '')::numeric as amount_num
          ) as amt
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'OZON'
            and amt.amount_num is not null
            and amt.amount_num <> 0
            and r.payload::jsonb ->> 'operation_type' in (
              'MarketplaceMarketingActionCostOperation',
              'MarketplaceSaleReviewsOperation',
              'OperationMarketplaceServicePremiumCashback',
              'MarketplaceServicePremiumCashbackIndividualPoints',
              'MarketplaceServicePremiumPromotion',
              'OperationElectronicServicesPromotionInSearch',
              'ItemAgentServiceStarsMembership',
              'MarketplaceServiceItemInstallment',
              'OperationElectronicServiceStencil',
              'OperationMarketplaceServiceItemElectronicServicesBrandShelf',
              'OperationSubscriptionPremium'
            )
      ) as t
      """.formatted(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

  private static final String WILDBERRIES_MARKETING_SELECT = """
      select
          :accountId      as account_id,
          :sourcePlatform as source_platform,
          t.source_event_id,
          t.order_id,
          t.operation_date,
          t.marketing_type,
          sum(t.marketing_charge_amount) as marketing_charge_amount,
          sum(t.marketing_refund_amount) as marketing_refund_amount,
          t.currency
      from (
          select
              r.payload::jsonb ->> 'rrd_id'                     as source_event_id,
              r.payload::jsonb ->> 'srid'                       as order_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date as operation_date,
              'WB_SELLER_PROMO'                                 as marketing_type,
              case
                when sp.seller_promo_discount_num > 0 then sp.seller_promo_discount_num
                else 0
              end                                               as marketing_charge_amount,
              case
                when sp.seller_promo_discount_num < 0 then -sp.seller_promo_discount_num
                else 0
              end                                               as marketing_refund_amount,
              coalesce(r.payload::jsonb ->> 'currency_name', 'руб') as currency
          from %1$s r
          cross join lateral (
              select nullif(r.payload::jsonb ->> 'seller_promo_discount', '')::numeric
                     as seller_promo_discount_num
          ) sp
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and sp.seller_promo_discount_num is not null
            and sp.seller_promo_discount_num <> 0

          union all

          select
              r.payload::jsonb ->> 'rrd_id'                     as source_event_id,
              r.payload::jsonb ->> 'srid'                       as order_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date as operation_date,
              'WB_SUPPLIER_PROMO'                               as marketing_type,
              case
                when sp.supplier_promo_num > 0 then sp.supplier_promo_num
                else 0
              end                                               as marketing_charge_amount,
              case
                when sp.supplier_promo_num < 0 then -sp.supplier_promo_num
                else 0
              end                                               as marketing_refund_amount,
              coalesce(r.payload::jsonb ->> 'currency_name', 'руб') as currency
          from %1$s r
          cross join lateral (
              select nullif(r.payload::jsonb ->> 'supplier_promo', '')::numeric as supplier_promo_num
          ) sp
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and sp.supplier_promo_num is not null
            and sp.supplier_promo_num <> 0

          union all

          select
              r.payload::jsonb ->> 'rrd_id'                     as source_event_id,
              r.payload::jsonb ->> 'srid'                       as order_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date as operation_date,
              'WB_CASHBACK_DISCOUNT'                            as marketing_type,
              case
                when cb.cashback_discount_num > 0 then cb.cashback_discount_num
                else 0
              end                                               as marketing_charge_amount,
              case
                when cb.cashback_discount_num < 0 then -cb.cashback_discount_num
                else 0
              end                                               as marketing_refund_amount,
              coalesce(r.payload::jsonb ->> 'currency_name', 'руб') as currency
          from %1$s r
          cross join lateral (
              select nullif(r.payload::jsonb ->> 'cashback_discount', '')::numeric
                     as cashback_discount_num
          ) cb
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and cb.cashback_discount_num is not null
            and cb.cashback_discount_num <> 0

          union all

          select
              r.payload::jsonb ->> 'rrd_id'                     as source_event_id,
              r.payload::jsonb ->> 'srid'                       as order_id,
              (r.payload::jsonb ->> 'rr_dt')::timestamptz::date as operation_date,
              'WB_CASHBACK_COMMISSION_CHANGE'                   as marketing_type,
              case
                when cb.cashback_commission_change_num > 0 then cb.cashback_commission_change_num
                else 0
              end                                               as marketing_charge_amount,
              case
                when cb.cashback_commission_change_num < 0 then -cb.cashback_commission_change_num
                else 0
              end                                               as marketing_refund_amount,
              coalesce(r.payload::jsonb ->> 'currency_name', 'руб') as currency
          from %1$s r
          cross join lateral (
              select nullif(r.payload::jsonb ->> 'cashback_commission_change', '')::numeric
                     as cashback_commission_change_num
          ) cb
          where r.account_id = :accountId
            and r.request_id = :requestId
            and r.marketplace = 'WILDBERRIES'
            and cb.cashback_commission_change_num is not null
            and cb.cashback_commission_change_num <> 0
      ) as t
      group by
          t.source_event_id,
          t.order_id,
          t.operation_date,
          t.marketing_type,
          t.currency
      """.formatted(RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(long accountId, String requestId) {
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS)) {
      selects.add(OZON_MARKETING_SELECT);
    }
    executeUpsert(selects, accountId, requestId, MarketplaceType.OZON.tag());
  }

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_WB_SALES_REPORT_DETAIL)) {
      selects.add(WILDBERRIES_MARKETING_SELECT);
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
    int rows = jdbcTemplate.update(sql, parameters);
    log.info(
        "Marketing facts upserted: accountId={}, sourcePlatform={}, requestId={}, rows={}",
        accountId,
        sourcePlatform,
        requestId,
        rows
    );
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
