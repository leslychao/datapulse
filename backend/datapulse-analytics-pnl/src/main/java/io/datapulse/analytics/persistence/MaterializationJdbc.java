package io.datapulse.analytics.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MaterializationJdbc {

  private final NamedParameterJdbcTemplate pg;
  private final JdbcTemplate ch;

  public MaterializationJdbc(
      NamedParameterJdbcTemplate pgJdbcTemplate,
      @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
    this.pg = pgJdbcTemplate;
    this.ch = clickhouseJdbcTemplate;
  }

  public NamedParameterJdbcTemplate pg() {
    return pg;
  }

  public JdbcTemplate ch() {
    return ch;
  }

  /**
   * Two-phase staging swap for full re-materialization.
   * Creates an empty staging table, populates it via the provided callback,
   * then atomically swaps staging ↔ live. On failure, drops the staging table
   * so the live table remains untouched.
   *
   * @param table           live table name
   * @param populateStaging callback that inserts data into the staging table name it receives
   */
  public void fullMaterializeWithSwap(String table, Consumer<String> populateStaging) {
    String staging = table + "_staging";
    ch.execute("DROP TABLE IF EXISTS " + staging);
    ch.execute("CREATE TABLE " + staging + " AS " + table);
    try {
      populateStaging.accept(staging);
      ch.execute("EXCHANGE TABLES " + table + " AND " + staging);
      log.debug("Staging swap completed: table={}", table);
    } catch (Exception e) {
      log.warn("Staging swap failed, rolling back: table={}", table, e);
      throw e;
    } finally {
      ch.execute("DROP TABLE IF EXISTS " + staging);
    }
  }

  public Instant getWatermark(String tableName) {
    List<Timestamp> results = ch.query(
        "SELECT last_materialized_at FROM materialization_watermark FINAL WHERE table_name = ?",
        (rs, rowNum) -> rs.getTimestamp(1),
        tableName);
    if (results.isEmpty()) {
      return null;
    }
    return results.get(0).toInstant();
  }

  public void updateWatermark(String tableName, Instant instant) {
    ch.update("""
        INSERT INTO materialization_watermark (table_name, last_materialized_at, ver)
        VALUES (?, ?, ?)
        """,
        tableName, Timestamp.from(instant), System.currentTimeMillis());
  }
}
