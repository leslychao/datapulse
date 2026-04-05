package io.datapulse.etl.persistence.clickhouse;

import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Batch INSERT of advertising fact rows into ClickHouse {@code fact_advertising}.
 * Uses {@code ReplacingMergeTree(ver)} for idempotent writes — later {@code ver} wins.
 */
@Slf4j
@Service
public class AdvertisingClickHouseWriter {

  private static final int BATCH_SIZE = 500;

  private static final String INSERT = """
      INSERT INTO fact_advertising (
          connection_id, source_platform, campaign_id, ad_date, marketplace_sku,
          views, clicks, spend, orders, ordered_units, ordered_revenue, canceled,
          ctr, cpc, cr, job_execution_id, ver, materialized_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
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

    clickhouseJdbcTemplate.batchUpdate(INSERT, rows, BATCH_SIZE,
        (ps, row) -> {
          ps.setLong(1, row.connectionId());
          ps.setString(2, row.sourcePlatform());
          ps.setLong(3, row.campaignId());
          ps.setDate(4, java.sql.Date.valueOf(row.adDate()));
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
        });

    log.info("Written {} advertising fact rows to ClickHouse, ver={}",
        rows.size(), ver);
  }
}
