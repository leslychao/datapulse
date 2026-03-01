package io.datapulse.etl.flow.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EtlExecutionRepo {

  private final JdbcTemplate jdbc;

  public EtlExecutionRepo(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean isTerminal(String requestId) {
    return jdbc.query(
        "select status from etl_execution where request_id=?",
        rs -> {
          if (!rs.next()) return false;
          String s = rs.getString(1);
          return "COMPLETED".equals(s) || "FAILED".equals(s);
        },
        requestId
    );
  }

  public void upsertExecutionNewIfAbsent(String requestId) {
    jdbc.update("""
      insert into etl_execution(request_id, status, total_sources, completed_sources, failed_sources, updated_at)
      values(?, 'NEW', 0, 0, 0, now())
      on conflict (request_id) do nothing
    """, requestId);
  }

  public void markInProgressAndTotalSources(String requestId, int totalSources) {
    jdbc.update("""
      update etl_execution
      set
        status = case when status='NEW' then 'IN_PROGRESS' else status end,
        total_sources = case when status='NEW' then ? else total_sources end,
        updated_at = now()
      where request_id = ?
    """, totalSources, requestId);
  }

  public void incCompletedAndMaybeFinishStopOnFailure(String requestId) {
    jdbc.update("""
      update etl_execution
      set
        completed_sources = completed_sources + 1,
        status = case
          when status in ('FAILED','COMPLETED') then status
          when failed_sources > 0 then 'FAILED'
          when (completed_sources + 1) >= total_sources then 'COMPLETED'
          else status
        end,
        updated_at = now()
      where request_id = ?
    """, requestId);
  }

  public void incFailedAndMaybeFinishStopOnFailure(String requestId) {
    jdbc.update("""
      update etl_execution
      set
        failed_sources = failed_sources + 1,
        status = 'FAILED',
        updated_at = now()
      where request_id = ?
    """, requestId);
  }
}
