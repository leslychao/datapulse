package io.datapulse.etl.materialization.dim.category;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.RawTableNames;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimCategoryJdbcRepository implements DimCategoryRepository {

  private static final String INSERT_TEMPLATE = """
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
        with recursive category_tree(source_platform, source_category_id, category_name, parent_id, parent_name, is_leaf, node) as (
            select
                '%1$s' as source_platform,
                (payload::jsonb ->> 'description_category_id')::bigint,
                payload::jsonb ->> 'category_name',
                null::bigint,
                null::text,
                case when jsonb_array_length(payload::jsonb -> 'children') = 0 then true else false end,
                payload::jsonb
            from %2$s
            where account_id = ? and request_id = ?

            union all

            select
                '%1$s',
                (child ->> 'description_category_id')::bigint,
                child ->> 'category_name',
                (parent.node ->> 'description_category_id')::bigint,
                parent.node ->> 'category_name',
                case when jsonb_array_length(child -> 'children') = 0 then true else false end,
                child
            from category_tree parent,
                 jsonb_array_elements(parent.node -> 'children') as child
        )
        select
            source_platform,
            source_category_id,
            category_name,
            parent_id,
            parent_name,
            is_leaf
        from category_tree
        where source_category_id is not null
        """.formatted(platform, RawTableNames.RAW_OZON_CATEGORY_TREE);

    String sql = INSERT_TEMPLATE.formatted(selectQuery);
    jdbcTemplate.update(sql, accountId, requestId);
  }

  @Override
  public void upsertWildberries(Long accountId, String requestId) {
    String platform = MarketplaceType.WILDBERRIES.tag();

    String wbSelectQuery = """
        select
            '%1$s' as source_platform,
            (parent.payload::jsonb ->> 'id')::bigint as source_category_id,
            parent.payload::jsonb ->> 'name' as category_name,
            null::bigint as parent_id,
            null::text as parent_name,
            false as is_leaf
        from %2$s parent
        where parent.account_id = ? and parent.request_id = ?

        union all

        select
            '%1$s' as source_platform,
            (subject.payload::jsonb ->> 'subjectID')::bigint as source_category_id,
            subject.payload::jsonb ->> 'subjectName' as category_name,
            (subject.payload::jsonb ->> 'parentID')::bigint as parent_id,
            parent.payload::jsonb ->> 'name' as parent_name,
            true as is_leaf
        from %3$s subject
        left join %2$s parent
          on (subject.payload::jsonb ->> 'parentID')::bigint = (parent.payload::jsonb ->> 'id')::bigint
        where subject.account_id = ? and subject.request_id = ?
          and parent.account_id = subject.account_id and parent.request_id = subject.request_id
        """.formatted(platform, RawTableNames.RAW_WB_CATEGORIES_PARENT,
        RawTableNames.RAW_WB_SUBJECTS);

    String sql = INSERT_TEMPLATE.formatted(wbSelectQuery);
    jdbcTemplate.update(sql, accountId, requestId, accountId, requestId);
  }
}
