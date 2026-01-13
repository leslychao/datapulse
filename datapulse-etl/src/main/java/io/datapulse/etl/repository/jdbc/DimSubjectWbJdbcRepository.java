package io.datapulse.etl.repository.jdbc;

import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.repository.DimSubjectWbRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DimSubjectWbJdbcRepository implements DimSubjectWbRepository {

  private static final String UPSERT_TEMPLATE = """
      insert into dim_subject_wb (
          account_id,
          source_subject_id,
          subject_name,
          parent_category_id,
          parent_category_name,
          created_at,
          updated_at
      )
      select
          account_id,
          source_subject_id,
          subject_name,
          parent_category_id,
          parent_category_name,
          now(),
          now()
      from (%s) as source
      on conflict (account_id, source_subject_id) do update
        set subject_name = excluded.subject_name,
            parent_category_id = excluded.parent_category_id,
            parent_category_name = excluded.parent_category_name,
            updated_at = now();
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsert(long accountId, String requestId) {
    Objects.requireNonNull(requestId, "requestId обязателен.");

    String selectQuery = """
        select distinct on ((s.payload::jsonb ->> 'subjectID')::bigint)
            s.account_id                                     as account_id,
            (s.payload::jsonb ->> 'subjectID')::bigint       as source_subject_id,
            s.payload::jsonb ->> 'subjectName'               as subject_name,
            (s.payload::jsonb ->> 'parentID')::bigint        as parent_category_id,
            s.payload::jsonb ->> 'parentName'                as parent_category_name,
            s.created_at                                     as created_at
        from %1$s s
        where s.account_id = ?
          and s.request_id = ?
          and (s.payload::jsonb ->> 'subjectID') is not null
          and nullif(s.payload::jsonb ->> 'subjectName', '') is not null
        order by
            (s.payload::jsonb ->> 'subjectID')::bigint,
            s.created_at desc
        """.formatted(RawTableNames.RAW_WB_SUBJECTS);

    jdbcTemplate.update(UPSERT_TEMPLATE.formatted(selectQuery), accountId, requestId);
  }
}
