package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.CommissionFactRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommissionFactJdbcRepository implements CommissionFactRepository {

  private static final Logger log = LoggerFactory.getLogger(CommissionFactJdbcRepository.class);

  private static final String UPSERT_TEMPLATE = """
      insert into fact_commission (
          account_id,
          source_platform,
          source_event_id,
          order_id,
          operation_date,
          commission_type,
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
          operation_date,
          commission_type,
          amount,
          currency,
          now(),
          now()
      from (
          %s
      ) as source
      on conflict (account_id, source_platform, source_event_id, commission_type)
      do update
      set
          order_id       = excluded.order_id,
          operation_date = excluded.operation_date,
          amount         = excluded.amount,
          currency       = excluded.currency,
          updated_at     = now()
      """;

  private static final String OZON_COMMISSION_SELECT = """
      select
          :accountId                                                                 as account_id,
          :sourcePlatform                                                            as source_platform,
          r.payload::jsonb ->> 'operation_id'                                        as source_event_id,
          nullif(r.payload::jsonb -> 'posting' ->> 'posting_number', '')             as order_id,
          (r.payload::jsonb ->> 'operation_date')::timestamptz::date                 as operation_date,
          'SALE_COMMISSION'                                                          as commission_type,
          - nullif(r.payload::jsonb ->> 'sale_commission', '')::numeric              as amount,
          'RUB'                                                                      as currency
      from %s r
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.marketplace = 'OZON'
        and nullif(r.payload::jsonb ->> 'sale_commission', '') is not null
        and nullif(r.payload::jsonb ->> 'sale_commission', '')::numeric <> 0
      """.formatted(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS);

  private static final String WILDBERRIES_COMMISSION_SELECT = """
      select
          :accountId                                                                 as account_id,
          :sourcePlatform                                                            as source_platform,
          r.payload::jsonb ->> 'rrd_id'                                              as source_event_id,
          r.payload::jsonb ->> 'srid'                                                as order_id,
          (r.payload::jsonb ->> 'rr_dt')::timestamptz::date                          as operation_date,
          'SALE_COMMISSION'                                                          as commission_type,
          nullif(r.payload::jsonb ->> 'ppvz_sales_commission', '')::numeric          as amount,
          coalesce(r.payload::jsonb ->> 'currency_name', 'руб')                      as currency
      from %1$s r
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.marketplace = 'WILDBERRIES'
        and nullif(r.payload::jsonb ->> 'ppvz_sales_commission', '') is not null
        and nullif(r.payload::jsonb ->> 'ppvz_sales_commission', '')::numeric <> 0

      union all

      select
          :accountId                                                                 as account_id,
          :sourcePlatform                                                            as source_platform,
          r.payload::jsonb ->> 'rrd_id'                                              as source_event_id,
          r.payload::jsonb ->> 'srid'                                                as order_id,
          (r.payload::jsonb ->> 'rr_dt')::timestamptz::date                          as operation_date,
          'ACQUIRING_FEE'                                                            as commission_type,
          nullif(r.payload::jsonb ->> 'acquiring_fee', '')::numeric                  as amount,
          coalesce(r.payload::jsonb ->> 'currency_name', 'руб')                      as currency
      from %1$s r
      where r.account_id = :accountId
        and r.request_id = :requestId
        and r.marketplace = 'WILDBERRIES'
        and nullif(r.payload::jsonb ->> 'acquiring_fee', '') is not null
        and nullif(r.payload::jsonb ->> 'acquiring_fee', '')::numeric <> 0
      """.formatted(RawTableNames.RAW_WB_SALES_REPORT_DETAIL);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(long accountId, String requestId) {
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_OZON_FINANCE_TRANSACTIONS)) {
      selects.add(OZON_COMMISSION_SELECT);
    }
    executeUpsert(selects, accountId, requestId, MarketplaceType.OZON.tag());
  }

  @Override
  public void upsertWildberries(long accountId, String requestId) {
    List<String> selects = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_WB_SALES_REPORT_DETAIL)) {
      selects.add(WILDBERRIES_COMMISSION_SELECT);
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
        "Commission facts upserted: accountId={}, sourcePlatform={}, requestId={}, rows={}",
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
