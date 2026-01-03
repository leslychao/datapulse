package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.PenaltiesFactRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PenaltiesFactJdbcRepository implements PenaltiesFactRepository {

  private static final Logger log = LoggerFactory.getLogger(PenaltiesFactJdbcRepository.class);

  private static final String UPSERT_TEMPLATE = """
      insert into fact_penalties (
          account_id,
          source_platform,
          source_event_id,
          order_id,
          penalty_type,
          penalty_ts,
          penalty_date,
          amount,
          currency,
          created_at,
          updated_at
      )
      select
          account_id,
          source_platform,
          source_event_id,
          order_id,
          penalty_type,
          penalty_ts,
          penalty_date,
          amount,
          currency,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, source_event_id, penalty_type)
      do update
      set
          order_id     = excluded.order_id,
          penalty_type = excluded.penalty_type,
          penalty_ts   = excluded.penalty_ts,
          penalty_date = excluded.penalty_date,
          amount       = excluded.amount,
          currency     = excluded.currency,
          updated_at   = now()
      """;

  private static final String OZON_PENALTIES_SELECT = """
      select
          :accountId                                                                 as account_id,
          :sourcePlatform                                                            as source_platform,
          r.payload::jsonb ->> 'operation_id'                                        as source_event_id,
          nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '')             as order_id,
          case
            when r.payload::jsonb ->> 'operation_type' = 'OperationMarketplaceWithHoldingForUndeliverableGoods'
              then 'NOT_DELIVERED'
            when r.payload::jsonb ->> 'operation_type' = 'OperationClaim'
              then 'QUALITY'
            else 'OTHER_PENALTY'
          end                                                                        as penalty_type,
          (r.payload::jsonb ->> 'operation_date')::timestamptz                       as penalty_ts,
          (r.payload::jsonb ->> 'operation_date')::timestamptz::date                 as penalty_date,
          abs(nullif(r.payload::jsonb ->> 'amount', '')::numeric)                    as amount,
          'RUB'                                                                      as currency
      from %s r
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.marketplace = 'OZON'
        and r.payload::jsonb ->> 'operation_type' in (
          'OperationMarketplaceWithHoldingForUndeliverableGoods',
          'OperationClaim'
        )
        and nullif(r.payload::jsonb ->> 'amount', '') is not null
        and nullif(r.payload::jsonb ->> 'amount', '')::numeric < 0
      """.formatted(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

  private static final String WILDBERRIES_PENALTIES_SELECT = """
      select
          :accountId                                                                 as account_id,
          :sourcePlatform                                                            as source_platform,
          r.payload::jsonb ->> 'rrd_id'                                              as source_event_id,
          r.payload::jsonb ->> 'srid'                                                as order_id,
          case
            when lower(coalesce(r.payload::jsonb ->> 'bonus_type_name', '')) like '%%невыполненный заказ%%'
              then 'NOT_DELIVERED'
            when lower(coalesce(r.payload::jsonb ->> 'bonus_type_name', '')) like '%%качест%%'
              then 'QUALITY'
            when lower(coalesce(r.payload::jsonb ->> 'bonus_type_name', '')) like '%%документ%%'
              then 'DOCUMENTS'
            when lower(coalesce(r.payload::jsonb ->> 'bonus_type_name', '')) like '%%регламент%%'
              or lower(coalesce(r.payload::jsonb ->> 'bonus_type_name', '')) like '%%срок%%'
              or lower(coalesce(r.payload::jsonb ->> 'bonus_type_name', '')) like '%%процесс%%'
              then 'PROCESS_VIOLATION'
            else 'OTHER_PENALTY'
          end                                                                        as penalty_type,
          (r.payload::jsonb ->> 'rr_dt')::timestamptz                                as penalty_ts,
          (r.payload::jsonb ->> 'rr_dt')::timestamptz::date                          as penalty_date,
          abs(nullif(r.payload::jsonb ->> 'penalty', '')::numeric)                   as amount,
          coalesce(r.payload::jsonb ->> 'currency_name', 'руб')                      as currency
      from %s r
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.marketplace = 'WILDBERRIES'
        and nullif(r.payload::jsonb ->> 'penalty', '') is not null
        and nullif(r.payload::jsonb ->> 'penalty', '')::numeric <> 0
      """.formatted(RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS)) {
      selects.add(OZON_PENALTIES_SELECT);
    }
    executeUpsert(selects, accountId, requestId, MarketplaceType.OZON.tag());
  }

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_WB_SALES_REPORT_DETAIL)) {
      selects.add(WILDBERRIES_PENALTIES_SELECT);
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
        "Penalty facts upserted: accountId={}, sourcePlatform={}, requestId={}, rows={}",
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
