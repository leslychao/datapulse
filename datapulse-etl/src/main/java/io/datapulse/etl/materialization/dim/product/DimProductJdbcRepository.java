package io.datapulse.etl.materialization.dim.product;

import io.datapulse.etl.RawTableNames;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimProductJdbcRepository implements DimProductRepository {

  private static final String INSERT_OZON_TEMPLATE = """
      insert into dim_product_ozon (
          account_id,
          product_id,
          offer_id,
          is_archived,
          has_fbo_stocks,
          has_fbs_stocks,
          created_at,
          updated_at
      )
      select
          account_id,
          product_id,
          offer_id,
          is_archived,
          has_fbo_stocks,
          has_fbs_stocks,
          now(),
          now()
      from (%s) as source
      on conflict (account_id, product_id) do update
        set offer_id = excluded.offer_id,
            is_archived = excluded.is_archived,
            has_fbo_stocks = excluded.has_fbo_stocks,
            has_fbs_stocks = excluded.has_fbs_stocks,
            updated_at = now();
      """;

  private static final String INSERT_WB_TEMPLATE = """
      insert into dim_product_wb (
          account_id,
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
          account_id,
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
      on conflict (account_id, nm_id) do update
        set imt_id = excluded.imt_id,
            vendor_code = excluded.vendor_code,
            subject_id = excluded.subject_id,
            subject_name = excluded.subject_name,
            brand = excluded.brand,
            title = excluded.title,
            updated_at = now();
      """;

  /**
   * Важно: дедупликация сделана через DISTINCT ON и сортировку по created_at. Если в RAW таблице
   * нет created_at, замени "created_at desc" на "id desc" (или на твою колонку времени/порядка).
   */
  private static final String OZON_SELECT = """
      select distinct on (product_id)
          account_id,
          product_id,
          offer_id,
          is_archived,
          has_fbo_stocks,
          has_fbs_stocks
      from (
          select
              account_id,
              (payload::jsonb ->> 'product_id')::bigint as product_id,
              payload::jsonb ->> 'offer_id' as offer_id,
              coalesce((payload::jsonb ->> 'archived')::boolean, false) as is_archived,
              coalesce((payload::jsonb ->> 'has_fbo_stocks')::boolean, false) as has_fbo_stocks,
              coalesce((payload::jsonb ->> 'has_fbs_stocks')::boolean, false) as has_fbs_stocks,
              created_at
          from %s
          where account_id = ? and request_id = ?
            and marketplace = 'OZON'
            and nullif(payload::jsonb ->> 'product_id', '') is not null
      ) as s
      order by product_id, created_at desc
      """;

  private static final String WB_SELECT = """
      select distinct on (nm_id)
          account_id,
          nm_id,
          imt_id,
          vendor_code,
          subject_id,
          subject_name,
          brand,
          title
      from (
          select
              account_id,
              (payload::jsonb ->> 'nmID')::bigint as nm_id,
              nullif(payload::jsonb ->> 'imtID', '')::bigint as imt_id,
              payload::jsonb ->> 'vendorCode' as vendor_code,
              nullif(payload::jsonb ->> 'subjectID', '')::bigint as subject_id,
              payload::jsonb ->> 'subjectName' as subject_name,
              payload::jsonb ->> 'brand' as brand,
              payload::jsonb ->> 'title' as title,
              created_at
          from %s
          where account_id = ? and request_id = ?
            and marketplace = 'WILDBERRIES'
            and nullif(payload::jsonb ->> 'nmID', '') is not null
      ) as s
      order by nm_id, created_at desc
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    String rawTable = RawTableNames.RAW_OZON_PRODUCTS;
    if (!relationExists(rawTable)) {
      return;
    }

    String sql = INSERT_OZON_TEMPLATE.formatted(
        OZON_SELECT.formatted(rawTable)
    );
    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    String rawTable = RawTableNames.RAW_WB_PRODUCTS;
    if (!relationExists(rawTable)) {
      return;
    }

    String sql = INSERT_WB_TEMPLATE.formatted(
        WB_SELECT.formatted(rawTable)
    );
    jdbcTemplate.update(sql, accountId, requestId);
  }

  private boolean relationExists(String relationName) {
    Boolean exists = jdbcTemplate.queryForObject(
        "select to_regclass(?) is not null",
        Boolean.class,
        relationName
    );
    return Boolean.TRUE.equals(exists);
  }
}
