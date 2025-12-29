package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimCategoryRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimCategoryJdbcRepository implements DimCategoryRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into dim_category (
          source_platform,
          source_category_id,
          category_name,
          parent_id,
          parent_name,
          is_leaf,
          created_at,
          updated_at
      )
      select
          source_platform,
          source_category_id,
          category_name,
          parent_id,
          parent_name,
          is_leaf,
          now(),
          now()
      from (%s) as source
      on conflict (source_platform, source_category_id) do update
        set category_name = excluded.category_name,
            parent_id = excluded.parent_id,
            parent_name = excluded.parent_name,
            is_leaf = excluded.is_leaf,
            updated_at = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsertOzon(Long accountId, String requestId) {
    String platform = MarketplaceType.OZON.tag();

    String selectQuery = """
        with recursive category_tree (
            source_platform,
            source_category_id,
            category_name,
            parent_id,
            parent_name,
            node
        ) as (
            -- корневые категории
            select
                '%1$s' as source_platform,
                (payload::jsonb ->> 'description_category_id')::bigint as source_category_id,
                payload::jsonb ->> 'category_name'                     as category_name,
                null::bigint                                          as parent_id,
                null::text                                            as parent_name,
                payload::jsonb                                        as node
            from %2$s
            where account_id = ? and request_id = ?
              and (payload::jsonb ->> 'description_category_id') is not null

            union all

            -- дочерние узлы (берём всех, без фильтра по disabled)
            select
                '%1$s'                                               as source_platform,
                (child ->> 'description_category_id')::bigint        as source_category_id,
                child ->> 'category_name'                            as category_name,
                parent.source_category_id                            as parent_id,
                parent.category_name                                 as parent_name,
                child                                                as node
            from category_tree parent
            join lateral jsonb_array_elements(
                     coalesce(parent.node -> 'children', '[]'::jsonb)
                 ) child
                 on (child ->> 'description_category_id') is not null
        )
        select
            ct.source_platform,
            ct.source_category_id,
            ct.category_name,
            ct.parent_id,
            ct.parent_name,
            not exists (
                select 1
                from category_tree child
                where child.parent_id = ct.source_category_id
            ) as is_leaf
        from category_tree ct
        """.formatted(platform, RawTableNames.RAW_OZON_CATEGORY_TREE);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    Objects.requireNonNull(accountId, "accountId обязателен.");
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        with parent_latest as (
            select distinct on ((p.payload::jsonb ->> 'id')::bigint)
                '%1$s'                              as source_platform,
                (p.payload::jsonb ->> 'id')::bigint as source_category_id,
                p.payload::jsonb ->> 'name'         as category_name,
                null::bigint                        as parent_id,
                null::text                          as parent_name,
                p.created_at                        as created_at
            from %2$s p
            where p.account_id = ?
              and p.request_id = ?
              and (p.payload::jsonb ->> 'id') is not null
            order by
                (p.payload::jsonb ->> 'id')::bigint,
                p.created_at desc
        ),
        parent_enriched as (
            select
                pl.source_platform,
                pl.source_category_id,
                pl.category_name,
                pl.parent_id,
                pl.parent_name,
                not exists (
                    select 1
                    from %3$s s
                    where s.account_id = ?
                      and s.request_id = ?
                      and (s.payload::jsonb ->> 'parentID')::bigint = pl.source_category_id
                      and nullif(s.payload::jsonb ->> 'subjectID','') is not null
                ) as is_leaf,
                pl.created_at
            from parent_latest pl
        ),
        subjects_latest as (
            select distinct on ((s.payload::jsonb ->> 'subjectID')::bigint)
                '%1$s'                                        as source_platform,
                (s.payload::jsonb ->> 'subjectID')::bigint    as source_category_id,
                s.payload::jsonb ->> 'subjectName'            as category_name,
                (s.payload::jsonb ->> 'parentID')::bigint     as parent_id,
                cp.payload::jsonb ->> 'name'                  as parent_name,
                true                                          as is_leaf,
                s.created_at                                  as created_at
            from %3$s s
            left join %2$s cp
              on cp.account_id = s.account_id
             and cp.request_id = s.request_id
             and (cp.payload::jsonb ->> 'id')::bigint =
                 (s.payload::jsonb ->> 'parentID')::bigint
            where s.account_id = ?
              and s.request_id = ?
              and nullif(s.payload::jsonb ->> 'subjectID','') is not null
            order by
                (s.payload::jsonb ->> 'subjectID')::bigint,
                s.created_at desc
        )
        select
            source_platform,
            source_category_id,
            category_name,
            parent_id,
            parent_name,
            is_leaf
        from (
            select
                source_platform,
                source_category_id,
                category_name,
                parent_id,
                parent_name,
                is_leaf
            from parent_enriched
            union all
            select
                source_platform,
                source_category_id,
                category_name,
                parent_id,
                parent_name,
                is_leaf
            from subjects_latest
        ) all_categories
        """.formatted(
        platform,
        RawTableNames.RAW_WB_CATEGORIES_PARENT,
        RawTableNames.RAW_WB_SUBJECTS
    );

    jdbcTemplate.update(
        UPSERT_TEMPLATE.formatted(selectQuery),
        accountId, requestId,
        accountId, requestId,
        accountId, requestId
    );
  }
}
