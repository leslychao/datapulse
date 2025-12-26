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

  private static final String SELECT_SOURCE_PRODUCT_IDS = """
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
        set offer_id             = excluded.offer_id,
            external_category_id = excluded.external_category_id,
            updated_at           = now();
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
        set offer_id             = excluded.offer_id,
            external_category_id = excluded.external_category_id,
            updated_at           = now();
      """;

  /**
   * WB из продаж: по payload.category маппимся на dim_category.category_name и в
   * external_category_id пишем dim_category.source_category_id.
   */
  private static final String UPSERT_WB_FROM_SALES = """
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
        s.external_category_id,
        now(),
        now()
    from (
        select distinct
            r.account_id                                      as account_id,
            nullif(r.payload::jsonb ->> 'nmId','')            as source_product_id,
            nullif(r.payload::jsonb ->> 'supplierArticle','') as offer_id,
            dc.source_category_id                             as external_category_id
        from %s r
        left join dim_category dc
          on dc.source_platform = '%s'
         and lower(trim(dc.category_name)) =
             lower(trim(nullif(r.payload::jsonb ->> 'category','')))
        where r.account_id = ?
          and r.request_id = ?
          and nullif(r.payload::jsonb ->> 'nmId','') is not null
          and dc.source_category_id is not null
    ) s
    on conflict (account_id, source_platform, source_product_id) do update
      set external_category_id = coalesce(
            dim_product.external_category_id,
            excluded.external_category_id
          ),
          offer_id   = excluded.offer_id,
          updated_at = now();
    """;

  private static final String UPSERT_OZON_FROM_POSTINGS_TEMPLATE = """
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
          null as external_category_id,
          now(),
          now()
      from (
          select distinct
              p.account_id                   as account_id,
              nullif(item ->> 'sku','')      as source_product_id,
              nullif(item ->> 'offer_id','') as offer_id
          from %s p
          join lateral jsonb_array_elements(p.payload::jsonb -> 'products') item on true
          where p.account_id = ?
            and p.request_id = ?
            and nullif(item ->> 'sku','') is not null
      ) s
      left join dim_product dp
        on dp.account_id        = s.account_id
       and dp.source_platform   = '%s'
       and dp.source_product_id = s.source_product_id
      where dp.id is null
      on conflict (account_id, source_platform, source_product_id) do update
        set offer_id   = excluded.offer_id,
            updated_at = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public List<Long> fetchSourceProductIds(long accountId, MarketplaceType marketplaceType) {
    return jdbcTemplate.queryForList(
        SELECT_SOURCE_PRODUCT_IDS,
        Long.class,
        accountId,
        marketplaceType.tag()
    );
  }

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    requireNotNull(accountId, "accountId");
    requireNotNull(requestId, "requestId");

    if (!relationExists(RawTableNames.RAW_OZON_PRODUCTS)
        || !relationExists(RawTableNames.RAW_OZON_PRODUCT_INFO)) {
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

  @Override
  public void upsertWildberriesFromSales(long accountId, String requestId) {
    requireNotNull(accountId, "accountId");
    requireNotNull(requestId, "requestId");

    if (!relationExists(RawTableNames.RAW_WB_SUPPLIER_SALES)) {
      return;
    }

    String sql = UPSERT_WB_FROM_SALES.formatted(
        MarketplaceType.WILDBERRIES.tag(),          // для insert.source_platform
        RawTableNames.RAW_WB_SUPPLIER_SALES,        // таблица sales
        MarketplaceType.WILDBERRIES.tag(),          // dim_category.source_platform
        MarketplaceType.WILDBERRIES.tag()           // dim_product.source_platform
    );

    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertOzonFromPostingsFbs(long accountId, String requestId) {
    requireNotNull(accountId, "accountId");
    requireNotNull(requestId, "requestId");

    if (!relationExists(RawTableNames.RAW_OZON_POSTINGS_FBS)) {
      return;
    }

    String sql = UPSERT_OZON_FROM_POSTINGS_TEMPLATE.formatted(
        MarketplaceType.OZON.tag(),
        RawTableNames.RAW_OZON_POSTINGS_FBS,
        MarketplaceType.OZON.tag()
    );

    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertOzonFromPostingsFbo(long accountId, String requestId) {
    requireNotNull(accountId, "accountId");
    requireNotNull(requestId, "requestId");

    if (!relationExists(RawTableNames.RAW_OZON_POSTINGS_FBO)) {
      return;
    }

    String sql = UPSERT_OZON_FROM_POSTINGS_TEMPLATE.formatted(
        MarketplaceType.OZON.tag(),
        RawTableNames.RAW_OZON_POSTINGS_FBO,
        MarketplaceType.OZON.tag()
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
