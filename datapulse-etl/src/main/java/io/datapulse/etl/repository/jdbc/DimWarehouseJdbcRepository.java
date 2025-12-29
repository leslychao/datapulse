package io.datapulse.etl.repository.jdbc;

import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimWarehouseRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimWarehouseJdbcRepository implements DimWarehouseRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into dim_warehouse (
          account_id,
          source_platform,
          external_warehouse_id,
          warehouse_name,
          fulfillment_model,
          is_active,
          created_at,
          updated_at
      )
      select
          s.account_id,
          s.source_platform,
          s.external_warehouse_id,
          s.warehouse_name,
          s.fulfillment_model,
          s.is_active,
          now(),
          now()
      from (
          select distinct on (u.account_id, u.source_platform, u.external_warehouse_id)
              u.account_id,
              u.source_platform,
              u.external_warehouse_id,
              u.warehouse_name,
              u.fulfillment_model,
              u.is_active
          from (
              %s
          ) u
          order by
              u.account_id,
              u.source_platform,
              u.external_warehouse_id,
              u.priority desc
      ) s
      on conflict (account_id, source_platform, external_warehouse_id) do update
        set warehouse_name = excluded.warehouse_name,
            fulfillment_model = excluded.fulfillment_model,
            is_active = excluded.is_active,
            updated_at = now();
      """;

  private static final String OZON_FBS_SELECT = """
      select
          t.account_id as account_id,
          'OZON'::text as source_platform,
          (t.payload::jsonb ->> 'warehouse_id')::text as external_warehouse_id,
          (t.payload::jsonb ->> 'name')::text as warehouse_name,
          case
            when coalesce((t.payload::jsonb ->> 'is_rfbs')::boolean, false) then 'RFBS'
            else 'FBS'
          end as fulfillment_model,
          lower(t.payload::jsonb ->> 'status') in ('created', 'working') as is_active,
          20 as priority
      from %s t
      where t.account_id = ? and t.request_id = ?
      """;

  private static final String OZON_FBO_SELECT = """
      select
          t.account_id as account_id,
          'OZON'::text as source_platform,
          (w ->> 'warehouse_id')::text as external_warehouse_id,
          (w ->> 'name')::text as warehouse_name,
          'FBO' as fulfillment_model,
          true as is_active,
          10 as priority
      from %s t,
           jsonb_array_elements(coalesce(t.payload::jsonb -> 'logistic_clusters', '[]'::jsonb)) c,
           jsonb_array_elements(coalesce(c -> 'warehouses', '[]'::jsonb)) w
      where t.account_id = ? and t.request_id = ?
      """;

  private static final String WB_FBW_SELECT = """
      select
          t.account_id as account_id,
          'WILDBERRIES'::text as source_platform,
          (t.payload::jsonb ->> 'id')::text as external_warehouse_id,
          (t.payload::jsonb ->> 'name')::text as warehouse_name,
          'FBO' as fulfillment_model,
          coalesce((t.payload::jsonb ->> 'active')::boolean, false) as is_active,
          20 as priority
      from %s t
      where t.account_id = ? and t.request_id = ?
      """;

  private static final String WB_FBS_OFFICES_SELECT = """
      select
          t.account_id as account_id,
          'WILDBERRIES'::text as source_platform,
          (t.payload::jsonb ->> 'id')::text as external_warehouse_id,
          (t.payload::jsonb ->> 'name')::text as warehouse_name,
          'FBS' as fulfillment_model,
          coalesce((t.payload::jsonb ->> 'selected')::boolean, false) as is_active,
          10 as priority
      from %s t
      where t.account_id = ? and t.request_id = ?
      """;

  private static final String WB_SELLER_SELECT = """
      select
          t.account_id as account_id,
          'WILDBERRIES'::text as source_platform,
          (t.payload::jsonb ->> 'officeId')::text as external_warehouse_id,
          (t.payload::jsonb ->> 'name')::text as warehouse_name,
          'FBS' as fulfillment_model,
          not coalesce((t.payload::jsonb ->> 'isDeleting')::boolean, false) as is_active,
          5 as priority
      from %s t
      where t.account_id = ? and t.request_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    List<String> selects = new ArrayList<>();

    if (tableExists(RawTableNames.RAW_OZON_WAREHOUSES_FBS)) {
      selects.add(OZON_FBS_SELECT.formatted(RawTableNames.RAW_OZON_WAREHOUSES_FBS));
    }
    if (tableExists(RawTableNames.RAW_OZON_WAREHOUSES_FBO)) {
      selects.add(OZON_FBO_SELECT.formatted(RawTableNames.RAW_OZON_WAREHOUSES_FBO));
    }

    executeUpsert(selects, accountId, requestId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    List<String> selects = new ArrayList<>();

    if (tableExists(RawTableNames.RAW_WB_WAREHOUSES_FBW)) {
      selects.add(WB_FBW_SELECT.formatted(RawTableNames.RAW_WB_WAREHOUSES_FBW));
    }
    if (tableExists(RawTableNames.RAW_WB_OFFICES_FBS)) {
      selects.add(WB_FBS_OFFICES_SELECT.formatted(RawTableNames.RAW_WB_OFFICES_FBS));
    }
    if (tableExists(RawTableNames.RAW_WB_WAREHOUSES_SELLER)) {
      selects.add(WB_SELLER_SELECT.formatted(RawTableNames.RAW_WB_WAREHOUSES_SELLER));
    }

    executeUpsert(selects, accountId, requestId);
  }

  private void executeUpsert(List<String> selects, Long accountId, String requestId) {
    if (selects.isEmpty()) {
      return;
    }

    String union = String.join("\nunion all\n", selects);
    String sql = UPSERT_TEMPLATE.formatted(union);

    jdbcTemplate.update(
        sql,
        preparedStatement -> bind(preparedStatement, selects.size(), accountId, requestId)
    );
  }

  private void bind(
      PreparedStatement preparedStatement,
      int selectCount,
      Long accountId,
      String requestId
  ) throws SQLException {
    int index = 1;
    for (int i = 0; i < selectCount; i++) {
      preparedStatement.setLong(index++, accountId);
      preparedStatement.setString(index++, requestId);
    }
  }

  private boolean tableExists(String tableName) {
    Boolean exists = jdbcTemplate.queryForObject(
        "select exists (select 1 from information_schema.tables where table_name = ?)",
        Boolean.class,
        tableName
    );
    return Boolean.TRUE.equals(exists);
  }
}
