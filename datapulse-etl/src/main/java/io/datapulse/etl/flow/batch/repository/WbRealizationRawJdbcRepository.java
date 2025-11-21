package io.datapulse.etl.flow.batch.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.raw.wb.WbRealizationRaw;
import io.datapulse.domain.exception.AppException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class WbRealizationRawJdbcRepository {

  private static final String INSERT_SQL = """
      insert into raw_realization_fact_wb
        (request_id, snapshot_id, account_id, marketplace, payload, created_at)
      values (?, ?, ?, ?, cast(? as jsonb), now())
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public void saveBatch(
      List<WbRealizationRaw> batch,
      String requestId,
      String snapshotId,
      Long accountId,
      MarketplaceType marketplace
  ) {
    if (batch.isEmpty()) {
      return;
    }

    jdbcTemplate.batchUpdate(
        INSERT_SQL,
        batch,
        batch.size(),
        (ps, raw) -> {
          ps.setObject(1, requestId);
          ps.setObject(2, snapshotId);
          ps.setLong(3, accountId);
          ps.setString(4, marketplace.name());
          ps.setString(5, toJson(raw));
        }
    );
  }

  private String toJson(WbRealizationRaw raw) {
    try {
      return objectMapper.writeValueAsString(raw);
    } catch (JsonProcessingException ex) {
      throw new AppException(MessageCodes.SERIALIZATION_ERROR, ex);
    }
  }
}
