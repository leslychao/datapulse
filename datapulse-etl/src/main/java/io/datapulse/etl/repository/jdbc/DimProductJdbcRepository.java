package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimProductRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimProductJdbcRepository implements DimProductRepository {

  private static final String SELECT_OZON_SKU = """
      select distinct
          source_product_id as sku
      from dim_product
      where account_id = ?
        and source_platform = ?
        and coalesce(source_product_id, '') <> ''
      order by sku
      """;

  private static final String UPSERT_OZON = """
      insert into dim_product (
          account_id,
          source_platform,
          source_product_id,
          offer_id,
          external_category_id,
          created_at,
          updated_at
      )
      select
          s.account_id,
          '%s' as source_platform,
          i.sku as source_product_id,
          s.offer_id,
          i.description_category_id as external_category_id,
          now(),
          now()
      from (
          select distinct on (account_id, product_id)
              account_id,
              (payload::jsonb ->> 'product_id')::bigint as product_id,
              payload::jsonb ->> 'offer_id' as offer_id,
              created_at
          from %s
          where account_id = ? and request_id = ?
            and marketplace = '%s'
            and nullif(payload::jsonb ->> 'product_id', '') is not null
          order by account_id, product_id, created_at desc
      ) s
      left join (
          select distinct on (account_id, product_id)
              account_id,
              (payload::jsonb ->> 'id')::bigint as product_id,
              payload::jsonb ->> 'sku' as sku,
              (payload::jsonb ->> 'description_category_id')::bigint as description_category_id,
              created_at
          from %s
          where account_id = ?
            and marketplace = '%s'
            and nullif(payload::jsonb ->> 'id', '') is not null
            and nullif(payload::jsonb ->> 'sku', '') is not null
            and nullif(payload::jsonb ->> 'description_category_id', '') is not null
          order by account_id, product_id, created_at desc
      ) i
        on i.account_id = s.account_id
       and i.product_id = s.product_id
      where i.sku is not null
      on conflict (account_id, source_platform, source_product_id) do update
        set offer_id = excluded.offer_id,
            external_category_id = excluded.external_category_id,
            updated_at = now();
      """;

  private static final String UPSERT_WB = """
      insert into dim_product (
          account_id,
          source_platform,
          source_product_id,
          offer_id,
          external_category_id,
          created_at,
          updated_at
      )
      select
          s.account_id,
          '%s' as source_platform,
          s.source_product_id,
          s.offer_id,
          s.subject_id as external_category_id,
          now(),
          now()
      from (
          select distinct on (account_id, nm_id)
              account_id,
              (payload::jsonb ->> 'nmID')::bigint as nm_id,
              (payload::jsonb ->> 'nmID') as source_product_id,
              payload::jsonb ->> 'vendorCode' as offer_id,
              nullif(payload::jsonb ->> 'subjectID', '')::bigint as subject_id,
              created_at
          from %s
          where account_id = ? and request_id = ?
            and marketplace = '%s'
            and nullif(payload::jsonb ->> 'nmID', '') is not null
          order by account_id, nm_id, created_at desc
      ) s
      on conflict (account_id, source_platform, source_product_id) do update
        set offer_id = excluded.offer_id,
            external_category_id = excluded.external_category_id,
            updated_at = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public List<Long> fetchSourceProductIds(long accountId, MarketplaceType marketplaceType) {
    return jdbcTemplate.queryForList(
        SELECT_OZON_SKU,
        Long.class,
        accountId,
        marketplaceType.tag()
    );
  }

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    requireNotNull(accountId, "accountId");
    requireNotNull(requestId, "requestId");

    if (!relationExists(RawTableNames.RAW_OZON_PRODUCTS)) {
      return;
    }

    String sql = UPSERT_OZON.formatted(
        MarketplaceType.OZON.tag(),
        RawTableNames.RAW_OZON_PRODUCTS,
        MarketplaceType.OZON.name(),
        RawTableNames.RAW_OZON_PRODUCT_INFO,
        MarketplaceType.OZON.name()
    );

    jdbcTemplate.update(sql, accountId, requestId, accountId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    requireNotNull(accountId, "accountId");
    requireNotNull(requestId, "requestId");

    if (!relationExists(RawTableNames.RAW_WB_PRODUCTS)) {
      return;
    }

    String sql = UPSERT_WB.formatted(
        MarketplaceType.WILDBERRIES.tag(),
        RawTableNames.RAW_WB_PRODUCTS,
        MarketplaceType.WILDBERRIES.name()
    );

    jdbcTemplate.update(sql, accountId, requestId);
  }

  private static void requireNotNull(Object value, String argumentName) {
    if (Objects.isNull(value)) {
      throw new IllegalArgumentException(
          "Аргумент '" + argumentName + "' не должен быть null."
      );
    }
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
