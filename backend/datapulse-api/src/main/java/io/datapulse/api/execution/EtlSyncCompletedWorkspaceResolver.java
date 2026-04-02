package io.datapulse.api.execution;

import java.util.Map;

import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * {@code ETL_SYNC_COMPLETED} outbox payload always includes {@code workspaceId} after ingest changes;
 * this resolver fills the gap for older messages and any edge replay where only {@code connectionId}
 * is present.
 */
@Component
@RequiredArgsConstructor
public class EtlSyncCompletedWorkspaceResolver {

  private final MarketplaceConnectionRepository connectionRepository;

  public Long resolveWorkspaceId(Map<String, Object> payload) {
    Long fromPayload = toLong(payload.get("workspaceId"));
    if (fromPayload != null) {
      return fromPayload;
    }
    Long connectionId = toLong(payload.get("connectionId"));
    if (connectionId == null) {
      return null;
    }
    return connectionRepository
        .findById(connectionId)
        .map(MarketplaceConnectionEntity::getWorkspaceId)
        .orElse(null);
  }

  private static Long toLong(Object value) {
    if (value instanceof Number num) {
      return num.longValue();
    }
    if (value instanceof String s) {
      String trimmed = StringUtils.trimToNull(s);
      if (trimmed == null) {
        return null;
      }
      try {
        return Long.parseLong(trimmed);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }
}
