package io.datapulse.etl.persistence;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Batch writer for {@code fact_advertising} in ClickHouse.
 * Uses {@code ReplacingMergeTree(ver)} for idempotent upserts.
 */
@Slf4j
@Repository
public class AdvertisingClickHouseWriter {

  private static final int BATCH_SIZE = 5000;

  private static final String INSERT_FACTS = """
      INSERT INTO fact_advertising (
          connection_id, source_platform, campaign_id, ad_date, marketplace_sku,
          views, clicks, spend, orders, ordered_units, ordered_revenue, canceled,
          ctr, cpc, cr, job_execution_id, ver, materialized_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final JdbcTemplate clickhouseJdbcTemplate;

  public AdvertisingClickHouseWriter(
      @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
    this.clickhouseJdbcTemplate = clickhouseJdbcTemplate;
  }

  public void writeFacts(List<AdvertisingFactRow> rows) {
    if (rows.isEmpty()) {
      return;
    }

    long ver = Instant.now().toEpochMilli();
    Timestamp now = Timestamp.from(Instant.now());

    clickhouseJdbcTemplate.batchUpdate(INSERT_FACTS, rows, BATCH_SIZE,
        (ps, row) -> {
          ps.setLong(1, row.connectionId());
          ps.setString(2, row.sourcePlatform());
          ps.setLong(3, row.campaignId());
          ps.setDate(4, Date.valueOf(row.adDate()));
          ps.setString(5, row.marketplaceSku());
          ps.setLong(6, row.views());
          ps.setLong(7, row.clicks());
          ps.setBigDecimal(8, row.spend());
          ps.setInt(9, row.orders());
          ps.setInt(10, row.orderedUnits());
          ps.setBigDecimal(11, row.orderedRevenue());
          ps.setInt(12, row.canceled());
          ps.setFloat(13, row.ctr());
          ps.setBigDecimal(14, row.cpc());
          ps.setFloat(15, row.cr());
          ps.setLong(16, row.jobExecutionId());
          ps.setLong(17, ver);
          ps.setTimestamp(18, now);
        });

    log.info("fact_advertising written: rows={}, ver={}", rows.size(), ver);
  }
}
