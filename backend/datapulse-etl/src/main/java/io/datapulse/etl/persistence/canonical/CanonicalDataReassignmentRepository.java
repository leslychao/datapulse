package io.datapulse.etl.persistence.canonical;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CanonicalDataReassignmentRepository {

  private final JdbcTemplate jdbc;

  private static final String[] TABLES_WITH_CONNECTION_ID = {
      "canonical_order",
      "canonical_sale",
      "canonical_return",
      "canonical_finance_entry",
      "canonical_promo_campaign",
      "canonical_advertising_campaign"
  };

  private static final String[] TABLES_WITH_MARKETPLACE_CONNECTION_ID = {
      "category",
      "warehouse",
      "marketplace_offer"
  };

  public int reassign(long fromConnectionId, long toConnectionId) {
    int total = 0;

    for (String table : TABLES_WITH_CONNECTION_ID) {
      int rows = jdbc.update(
          "UPDATE " + table + " SET connection_id = ? WHERE connection_id = ?",
          toConnectionId, fromConnectionId);
      total += rows;
      if (rows > 0) {
        log.info("Reassigned canonical data: table={}, from={}, to={}, rows={}",
            table, fromConnectionId, toConnectionId, rows);
      }
    }

    for (String table : TABLES_WITH_MARKETPLACE_CONNECTION_ID) {
      int rows = jdbc.update(
          "UPDATE " + table + " SET marketplace_connection_id = ? WHERE marketplace_connection_id = ?",
          toConnectionId, fromConnectionId);
      total += rows;
      if (rows > 0) {
        log.info("Reassigned canonical data: table={}, from={}, to={}, rows={}",
            table, fromConnectionId, toConnectionId, rows);
      }
    }

    return total;
  }
}
