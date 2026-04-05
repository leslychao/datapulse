package io.datapulse.api.execution;

import java.util.Map;
import java.util.Optional;

import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Single place to parse {@code ETL_SYNC_COMPLETED} JSON maps: numeric IDs, optional {@code
 * workspaceId} fallback from {@code connectionId} for legacy messages.
 */
@Component
@RequiredArgsConstructor
public class EtlSyncCompletedPayloadResolver {

  private final MarketplaceConnectionRepository connectionRepository;

  /**
   * Workspace id for mismatch checks and other workspace-scoped reactions.
   *
   * @return empty when neither payload nor DB can supply a workspace
   */
  public Optional<Long> resolveWorkspaceId(Map<String, Object> payload) {
    return Optional.ofNullable(resolveWorkspaceIdIncludingFallback(payload));
  }

  /**
   * Full payload for post-sync triggers that need connection + job ids.
   */
  public Optional<EtlSyncCompletedPayload> resolveForPostSyncTriggers(
      Map<String, Object> payload) {
    Long connectionId = toLong(payload.get("connectionId"));
    Long jobExecutionId = toLong(payload.get("jobExecutionId"));
    if (connectionId == null || jobExecutionId == null) {
      return Optional.empty();
    }
    Long workspaceId = resolveWorkspaceIdIncludingFallback(payload);
    if (workspaceId == null) {
      return Optional.empty();
    }
    return Optional.of(new EtlSyncCompletedPayload(workspaceId, connectionId, jobExecutionId));
  }

  private Long resolveWorkspaceIdIncludingFallback(Map<String, Object> payload) {
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
