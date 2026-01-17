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

  private static final String SQL_TEMPLATE = """
      update user_profile up
         set last_activity_at = v.last_seen
        from (values %s) as v(profile_id, last_seen)
       where up.id = v.profile_id
         and (up.last_activity_at is null or up.last_activity_at < v.last_seen)
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public void updateLastActivityAtIfGreater(Map<Long, Instant> lastSeenByProfileId) {
    if (lastSeenByProfileId.isEmpty()) {
      return;
    }

    StringBuilder valuesSql = new StringBuilder();
    MapSqlParameterSource params = new MapSqlParameterSource();

    int index = 0;
    for (Map.Entry<Long, Instant> entry : lastSeenByProfileId.entrySet()) {
      Long profileId = entry.getKey();
      Instant lastSeen = entry.getValue();
      if (profileId == null || lastSeen == null) {
        continue;
      }

      if (index > 0) {
        valuesSql.append(", ");
      }

      String idParam = "profileId" + index;
      String tsParam = "lastSeen" + index;

      valuesSql.append("(:").append(idParam).append(", :").append(tsParam).append(")");

      OffsetDateTime lastSeenTs = OffsetDateTime.ofInstant(lastSeen, ZoneOffset.UTC);

      params.addValue(idParam, profileId);
      params.addValue(tsParam, lastSeenTs, Types.TIMESTAMP_WITH_TIMEZONE);

      index++;
    }

    if (index == 0) {
      return;
    }

    String sql = SQL_TEMPLATE.formatted(valuesSql);
    jdbcTemplate.update(sql, params);
  }
}
