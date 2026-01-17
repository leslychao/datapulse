package io.datapulse.core.repository.useractivity;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcUserActivityRepository implements UserActivityRepository {

  private static final String SQL_UPDATE_LAST_ACTIVITY_AT = """
      update user_profile
         set last_activity_at = :lastSeen
       where id = :profileId
         and (last_activity_at is null or last_activity_at < :lastSeen)
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void updateLastActivityAtIfGreater(Map<Long, Instant> lastSeenByProfileId) {
    if (lastSeenByProfileId.isEmpty()) {
      return;
    }

    MapSqlParameterSource[] batch = new MapSqlParameterSource[lastSeenByProfileId.size()];
    int index = 0;

    for (Map.Entry<Long, Instant> entry : lastSeenByProfileId.entrySet()) {
      Long profileId = entry.getKey();
      Instant lastSeen = entry.getValue();

      if (profileId == null || lastSeen == null) {
        continue;
      }

      OffsetDateTime lastSeenTs = OffsetDateTime.ofInstant(lastSeen, ZoneOffset.UTC);

      batch[index] = new MapSqlParameterSource()
          .addValue("profileId", profileId)
          .addValue("lastSeen", lastSeenTs, Types.TIMESTAMP_WITH_TIMEZONE);

      index++;
    }

    if (index == 0) {
      return;
    }

    MapSqlParameterSource[] effectiveBatch = batch;
    if (index != batch.length) {
      effectiveBatch = new MapSqlParameterSource[index];
      System.arraycopy(batch, 0, effectiveBatch, 0, index);
    }

    jdbcTemplate.batchUpdate(SQL_UPDATE_LAST_ACTIVITY_AT, effectiveBatch);
  }
}
