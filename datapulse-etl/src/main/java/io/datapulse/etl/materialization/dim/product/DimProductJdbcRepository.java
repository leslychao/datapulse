package io.datapulse.etl.materialization.dim.product;

import io.datapulse.etl.RawTableNames;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimProductJdbcRepository implements DimProductRepository {

  private static final String INSERT_OZON_TEMPLATE = """
      insert into dim_product_ozon (
          product_id,
          offer_id,
          title,
          is_archived,
          has_fbo_stocks,
          has_fbs_stocks,
          created_at,
          updated_at
      )
      select
          product_id,
          offer_id,
          title,
          is_archived,
          has_fbo_stocks,
          has_fbs_stocks,
          now(),
          now()
      from (%s) as source
      on conflict (product_id) do update
        set offer_id = excluded.offer_id,
            title = excluded.title,
            is_archived = excluded.is_archived,
            has_fbo_stocks = excluded.has_fbo_stocks,
            has_fbs_stocks = excluded.has_fbs_stocks,
            updated_at = now();
      """;

  private static final String INSERT_WB_TEMPLATE = """
      insert into dim_product_wb (
          nm_id,
          imt_id,
          vendor_code,
          subject_id,
          subject_name,
          brand,
          title,
          created_at,
          updated_at
      )
      select
          nm_id,
          imt_id,
          vendor_code,
          subject_id,
          subject_name,
          brand,
          title,
          now(),
          now()
      from (%s) as source
      on conflict (nm_id) do update
        set imt_id = excluded.imt_id,
            vendor_code = excluded.vendor_code,
            subject_id = excluded.subject_id,
            subject_name = excluded.subject_name,
            brand = excluded.brand,
            title = excluded.title,
            updated_at = now();
      """;

  private static final String OZON_SELECT_TEMPLATE = """
      select
          (payload::jsonb ->> 'product_id')::bigint as product_id,
          payload::jsonb ->> 'offer_id' as offer_id,
          null::text as title,
          coalesce((payload::jsonb ->> 'archived')::boolean, false) as is_archived,
          coalesce((payload::jsonb ->> 'has_fbo_stocks')::boolean, false) as has_fbo_stocks,
          coalesce((payload::jsonb ->> 'has_fbs_stocks')::boolean, false) as has_fbs_stocks
      from %s
      where account_id = ? and request_id = ?
      """;

  private static final String WB_SELECT_TEMPLATE = """
      select
          (payload::jsonb ->> 'nmID')::bigint as nm_id,
          (payload::jsonb ->> 'imtID')::bigint as imt_id,
          payload::jsonb ->> 'vendorCode' as vendor_code,
          (payload::jsonb ->> 'subjectID')::bigint as subject_id,
          payload::jsonb ->> 'subjectName' as subject_name,
          payload::jsonb ->> 'brand' as brand,
          payload::jsonb ->> 'title' as title
      from %s
      where account_id = ? and request_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    List<String> selectQueries = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_OZON_PRODUCTS)) {
      selectQueries.add(OZON_SELECT_TEMPLATE.formatted(RawTableNames.RAW_OZON_PRODUCTS));
    }
    executeUpsert(INSERT_OZON_TEMPLATE, selectQueries, accountId, requestId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    List<String> selectQueries = new ArrayList<>();
    if (tableExists(RawTableNames.RAW_WB_PRODUCTS)) {
      selectQueries.add(WB_SELECT_TEMPLATE.formatted(RawTableNames.RAW_WB_PRODUCTS));
    }
    executeUpsert(INSERT_WB_TEMPLATE, selectQueries, accountId, requestId);
  }

  private void executeUpsert(
      String insertTemplate,
      List<String> selectQueries,
      Long accountId,
      String requestId
  ) {
    if (selectQueries.isEmpty()) {
      return;
    }

    String unionQueries = String.join("\nunion all\n", selectQueries);
    String sql = insertTemplate.formatted(unionQueries);

    int paramsPerQuery = 2;
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
