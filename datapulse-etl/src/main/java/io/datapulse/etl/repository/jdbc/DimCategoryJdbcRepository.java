package io.datapulse.etl.repository.jdbc;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimCategoryRepository;
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
              and coalesce((payload::jsonb ->> 'disabled')::boolean, false) = false

            union all

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
                and coalesce((child ->> 'disabled')::boolean, false) = false
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
    String platform = MarketplaceType.WILDBERRIES.tag();

    String selectQuery = """
        with source_union as (
            select
                '%1$s' as source_platform,
                (parent.payload::jsonb ->> 'id')::bigint as source_category_id,
                parent.payload::jsonb ->> 'name'        as category_name,
                null::bigint                            as parent_id,
                null::text                              as parent_name,
                not exists (
                    select 1
                    from %3$s s
                    where (s.payload::jsonb ->> 'parentID')::bigint = (parent.payload::jsonb ->> 'id')::bigint
                      and s.account_id = parent.account_id
                      and s.request_id = parent.request_id
                ) as is_leaf
            from %2$s parent
            where parent.account_id = ? and parent.request_id = ?
              and (parent.payload::jsonb ->> 'id') is not null

            union all

            select
                '%1$s' as source_platform,
                (subject.payload::jsonb ->> 'subjectID')::bigint as source_category_id,
                subject.payload::jsonb ->> 'subjectName'         as category_name,
                (subject.payload::jsonb ->> 'parentID')::bigint  as parent_id,
                subject.payload::jsonb ->> 'parentName'          as parent_name,
                true                                             as is_leaf
            from %3$s subject
            where subject.account_id = ? and subject.request_id = ?
              and (subject.payload::jsonb ->> 'subjectID') is not null
        )
        select distinct on (source_platform, source_category_id)
            source_platform,
            source_category_id,
            category_name,
            parent_id,
            parent_name,
            is_leaf
        from source_union
        order by
            source_platform,
            source_category_id,
            (case when parent_id is not null then 1 else 0 end) desc
        """.formatted(
        platform,
        RawTableNames.RAW_WB_CATEGORIES_PARENT,
        RawTableNames.RAW_WB_SUBJECTS
    );

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId, accountId,
        requestId);
  }
}
