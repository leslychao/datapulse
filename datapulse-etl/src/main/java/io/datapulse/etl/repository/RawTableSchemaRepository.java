package io.datapulse.etl.repository;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RawTableSchemaRepository {

  private final JdbcTemplate jdbcTemplate;

  public void ensureTableExists(String tableName) {
    List<String> ddl = resolveDDl(tableName);

    try {
      if (tableExists(tableName)) {
        return;
      }

      ddl.forEach(jdbcTemplate::execute);
      log.info("RAW table '{}' created", tableName);
    } catch (DataAccessException ex) {
      log.error("RAW table '{}' creation failed", tableName, ex);
      throw new AppException(MessageCodes.RAW_TABLE_INIT_FAILED, tableName);
    }
  }

  private boolean tableExists(String tableName) {
    Boolean exists = jdbcTemplate.queryForObject(
        "select to_regclass(?::text) is not null",
        Boolean.class,
        tableName
    );
    return Boolean.TRUE.equals(exists);
  }

  private List<String> resolveDDl(String tableName) {
    if (tableName.startsWith("raw_")) {
      return rawSnapshotDDl(tableName);
    }

    throw new AppException(MessageCodes.RAW_TABLE_UNSUPPORTED, tableName);
  }

  private static List<String> rawSnapshotDDl(String tableName) {
    return List.of(
        """
            create table if not exists %s
            (
              id          bigserial primary key,
              request_id  varchar(64) not null,
              account_id  bigint      not null,
              marketplace varchar(32) not null,
              payload     jsonb       not null,
              created_at  timestamptz not null default now()
            );
            """.formatted(tableName),
        """
            create index if not exists idx_%s_request
              on %s (request_id);
            """.formatted(tableName, tableName),
        """
            create index if not exists idx_%s_acc_mp
              on %s (account_id, marketplace);
            """.formatted(tableName, tableName)
    );
  }
}
