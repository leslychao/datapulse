package io.datapulse.integration.domain;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.datapulse.integration.api.ConnectionSyncHealthResponse;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConnectionSyncHealthService {

  private static final int FRESH_SUCCESS_WITHIN_HOURS = 24;

  private final MarketplaceConnectionRepository connectionRepository;
  private final MarketplaceSyncStateRepository syncStateRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<ConnectionSyncHealthResponse> listForWorkspace(long workspaceId) {
    return connectionRepository
        .findAllByWorkspaceIdAndStatusNot(workspaceId, ConnectionStatus.ARCHIVED.name())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<ConnectionSyncHealthResponse> summarize(long connectionId) {
    return connectionRepository.findById(connectionId).map(this::toResponse);
  }

  private ConnectionSyncHealthResponse toResponse(MarketplaceConnectionEntity connection) {
    List<MarketplaceSyncStateEntity> states =
        syncStateRepository.findAllByMarketplaceConnectionId(connection.getId());
    List<MarketplaceSyncStateEntity> relevant =
        states.stream().filter(s -> !"DISABLED".equals(s.getStatus())).toList();

    OffsetDateTime maxSuccess = relevant.stream()
        .map(MarketplaceSyncStateEntity::getLastSuccessAt)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);

    SyncHealth health;
    if (relevant.stream().anyMatch(s -> "ERROR".equals(s.getStatus()))) {
      health = SyncHealth.ERROR;
    } else if (relevant.stream().anyMatch(s -> "SYNCING".equals(s.getStatus()))) {
      health = SyncHealth.SYNCING;
    } else if (maxSuccess == null
        || maxSuccess.isBefore(
            now().minus(FRESH_SUCCESS_WITHIN_HOURS, ChronoUnit.HOURS))) {
      health = SyncHealth.STALE;
    } else {
      health = SyncHealth.OK;
    }

    String lastSuccessIso = maxSuccess != null ? maxSuccess.toString() : null;
    return new ConnectionSyncHealthResponse(
        connection.getId(), connection.getName(), lastSuccessIso, health);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
