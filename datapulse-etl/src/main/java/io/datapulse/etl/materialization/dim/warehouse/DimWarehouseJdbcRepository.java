package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.etl.RawTableNames;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimWarehouseJdbcRepository implements DimWarehouseRepository {

  private static final String INSERT_TEMPLATE = """
      insert into %s (
          warehouse_id,
          warehouse_type,
          name,
          is_active,
          created_at,
          updated_at
      )
      select warehouse_id, warehouse_type, name, is_active, now(), now()
      from (%s) as source
      on conflict (warehouse_id) do update
        set warehouse_type = excluded.warehouse_type,
            name = excluded.name,
            is_active = excluded.is_active,
            updated_at = now();
      """;

  private static final String OZON_FBS_SELECT_TEMPLATE = """
      select
          (payload::jsonb ->> 'warehouse_id')::bigint as warehouse_id,
          'FBS_POINT' as warehouse_type,
          payload::jsonb ->> 'name' as name,
          lower(payload::jsonb ->> 'status') in ('created', 'working') as is_active
      from %s
      where account_id = ? and request_id = ?
      """;

  private static final String OZON_FBO_SELECT_TEMPLATE = """
      select
          (warehouse ->> 'warehouse_id')::bigint as warehouse_id,
          'FBO' as warehouse_type,
          warehouse ->> 'name' as name,
          true as is_active
      from %s,
           jsonb_array_elements(coalesce(payload::jsonb -> 'logistic_clusters', '[]'::jsonb)) cluster,
           jsonb_array_elements(coalesce(cluster -> 'warehouses', '[]'::jsonb)) warehouse
      where account_id = ? and request_id = ?
      """;

  private static final String WB_FBW_SELECT_TEMPLATE = """
      select
          (payload::jsonb ->> 'id')::bigint as warehouse_id,
          'FBO' as warehouse_type,
          payload::jsonb ->> 'name' as name,
          (payload::jsonb ->> 'active')::boolean as is_active
      from %s
      where account_id = ? and request_id = ?
      """;

  private static final String WB_FBS_OFFICES_SELECT_TEMPLATE = """
      select
          (payload::jsonb ->> 'id')::bigint as warehouse_id,
          'FBS_POINT' as warehouse_type,
          payload::jsonb ->> 'name' as name,
          coalesce((payload::jsonb ->> 'selected')::boolean, false) as is_active
      from %s
      where account_id = ? and request_id = ?
      """;

  private static final String WB_SELLER_SELECT_TEMPLATE = """
      select
          (payload::jsonb ->> 'id')::bigint as warehouse_id,
          'SELLER' as warehouse_type,
          payload::jsonb ->> 'name' as name,
          not coalesce((payload::jsonb ->> 'isDeleting')::boolean, false) as is_active
      from %s
      where account_id = ? and request_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    List<String> selectQueries = new ArrayList<>();

    if (tableExists(RawTableNames.RAW_OZON_WAREHOUSES_FBS)) {
      selectQueries.add(OZON_FBS_SELECT_TEMPLATE.formatted(RawTableNames.RAW_OZON_WAREHOUSES_FBS));
    }

    if (tableExists(RawTableNames.RAW_OZON_WAREHOUSES_FBO)) {
      selectQueries.add(OZON_FBO_SELECT_TEMPLATE.formatted(RawTableNames.RAW_OZON_WAREHOUSES_FBO));
    }

    executeUpsert("dim_warehouse_ozon", selectQueries, accountId, requestId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    List<String> selectQueries = new ArrayList<>();

    if (tableExists(RawTableNames.RAW_WB_WAREHOUSES_FBW)) {
      selectQueries.add(WB_FBW_SELECT_TEMPLATE.formatted(RawTableNames.RAW_WB_WAREHOUSES_FBW));
    }

    if (tableExists(RawTableNames.RAW_WB_OFFICES_FBS)) {
      selectQueries.add(WB_FBS_OFFICES_SELECT_TEMPLATE.formatted(RawTableNames.RAW_WB_OFFICES_FBS));
    }

    if (tableExists(RawTableNames.RAW_WB_WAREHOUSES_SELLER)) {
      selectQueries.add(WB_SELLER_SELECT_TEMPLATE.formatted(RawTableNames.RAW_WB_WAREHOUSES_SELLER));
    }

    executeUpsert("dim_warehouse_wb", selectQueries, accountId, requestId);
  }

  private void executeUpsert(String tableName, List<String> selectQueries, Long accountId,
      String requestId) {
    if (selectQueries.isEmpty()) {
      return;
    }

    String unionQueries = String.join("\nunion all\n", selectQueries);
    String sql = INSERT_TEMPLATE.formatted(tableName, unionQueries);

    int paramsPerQuery = 2; // account_id, request_id
    Object[] params = new Object[selectQueries.size() * paramsPerQuery];
    for (int i = 0; i < selectQueries.size(); i++) {
      params[i * paramsPerQuery] = accountId;
      params[i * paramsPerQuery + 1] = requestId;
    }

    jdbcTemplate.update(sql, params);
  }

  private boolean tableExists(String table) {
    Boolean exists = jdbcTemplate.queryForObject(
        "select exists (select 1 from information_schema.tables where table_name = ?)",
        Boolean.class,
        table
    );
    return Boolean.TRUE.equals(exists);
  }
}
