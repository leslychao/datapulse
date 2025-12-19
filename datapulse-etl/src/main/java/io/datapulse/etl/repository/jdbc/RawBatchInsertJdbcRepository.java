package io.datapulse.etl.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RawBatchInsertJdbcRepository {

  private static final String INSERT_SQL_TEMPLATE = """
      insert into %s
        (request_id, account_id, marketplace, payload, created_at)
      values (?, ?, ?, cast(? as jsonb), now())
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final RawTableSchemaJdbcRepository rawTableSchemaJdbcRepository;

  public <T> void saveBatch(
      List<T> batch,
      String tableName,
      String requestId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    if (batch.isEmpty()) {
      return;
    }

    rawTableSchemaJdbcRepository.ensureTableExists(tableName);

    String sql = INSERT_SQL_TEMPLATE.formatted(tableName);

    jdbcTemplate.batchUpdate(
        sql,
        batch,
        batch.size(),
        (ps, raw) -> {
          ps.setObject(1, requestId);
          ps.setLong(2, accountId);
          ps.setString(3, marketplace.name());
          ps.setString(4, toJson(raw));
        }
    );
  }

  public long countByRequestId(String tableName, String requestId) {
    rawTableSchemaJdbcRepository.ensureTableExists(tableName);
    String sql = "select count(*) from %s where request_id = ?".formatted(tableName);
    Long result = jdbcTemplate.queryForObject(sql, Long.class, requestId);
    return result != null ? result : 0L;
  }

  private <T> String toJson(T raw) {
    try {
      return objectMapper.writeValueAsString(raw);
    } catch (JsonProcessingException ex) {
      throw new AppException(MessageCodes.SERIALIZATION_ERROR, ex);
    }
  }
}
