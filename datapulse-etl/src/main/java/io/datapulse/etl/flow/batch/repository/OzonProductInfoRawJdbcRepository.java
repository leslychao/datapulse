package io.datapulse.etl.flow.batch.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.raw.ozon.OzonProductInfoRaw;
import io.datapulse.domain.exception.AppException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OzonProductInfoRawJdbcRepository {

  private static final String TABLE_NAME = "raw_product_info_ozon";

  private static final String CREATE_TABLE_SQL = """
      create table if not exists raw_product_info_ozon (
          id          bigserial primary key,
          account_id  bigint       not null,
          marketplace varchar(32)  not null,
          payload     jsonb        not null,
          created_at  timestamptz  not null default now()
      )
      """;

  private static final String INSERT_SQL = """
      insert into raw_product_info_ozon (account_id, marketplace, payload)
      values (?, ?, cast(? as jsonb))
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  private final AtomicBoolean tableInitialized = new AtomicBoolean(false);

  public void saveBatch(
      List<OzonProductInfoRaw> batch,
      Long accountId,
      MarketplaceType marketplace
  ) {
    if (batch.isEmpty()) {
      return;
    }

    ensureTableExists();

    jdbcTemplate.batchUpdate(
        INSERT_SQL,
        batch,
        batch.size(),
        (ps, raw) -> {
          ps.setLong(1, accountId);
          ps.setString(2, marketplace.name());
          ps.setString(3, toJson(raw));
        }
    );
  }

  private void ensureTableExists() {
    if (tableInitialized.get()) {
      return;
    }
    synchronized (tableInitialized) {
      if (tableInitialized.get()) {
        return;
      }

      log.info("Initializing raw table for Ozon product info: tableName={}", TABLE_NAME);
      jdbcTemplate.execute(CREATE_TABLE_SQL);
      tableInitialized.set(true);
    }
  }

  private String toJson(OzonProductInfoRaw raw) {
    try {
      return objectMapper.writeValueAsString(raw);
    } catch (JsonProcessingException ex) {
      throw new AppException(MessageCodes.SERIALIZATION_ERROR, ex);
    }
  }
}
