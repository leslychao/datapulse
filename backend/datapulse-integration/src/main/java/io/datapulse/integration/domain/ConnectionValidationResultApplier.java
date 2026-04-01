package io.datapulse.integration.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionValidationResultApplier {

  private final MarketplaceConnectionRepository connectionRepository;
  private final MarketplaceSyncStateRepository syncStateRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void applySuccess(Long connectionId, String externalAccountId) {
    MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
        .orElseThrow(() -> NotFoundException.connection(connectionId));

    String oldStatus = connection.getStatus();
    connection.setStatus(ConnectionStatus.ACTIVE.name());
    connection.setExternalAccountId(externalAccountId);
    connection.setLastCheckAt(OffsetDateTime.now());
    connection.setLastSuccessAt(OffsetDateTime.now());
    connection.setLastErrorCode(null);
    connection.setLastErrorAt(null);
    connectionRepository.save(connection);

    if (ConnectionStatus.PENDING_VALIDATION.name().equals(oldStatus)) {
      createSyncStatesForConnection(connection);
    }

    eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
        connectionId, oldStatus, ConnectionStatus.ACTIVE.name(), "validation_success"));
  }

  @Transactional
  public void applyFailure(Long connectionId, String errorCode) {
    MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
        .orElseThrow(() -> NotFoundException.connection(connectionId));

    String oldStatus = connection.getStatus();
    connection.setStatus(ConnectionStatus.AUTH_FAILED.name());
    connection.setLastCheckAt(OffsetDateTime.now());
    connection.setLastErrorAt(OffsetDateTime.now());
    connection.setLastErrorCode(errorCode);
    connectionRepository.save(connection);

    eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
        connectionId, oldStatus, ConnectionStatus.AUTH_FAILED.name(), "validation_failure"));
  }

  private void createSyncStatesForConnection(MarketplaceConnectionEntity connection) {
    List<MarketplaceSyncStateEntity> existing =
        syncStateRepository.findAllByMarketplaceConnectionId(connection.getId());
    Set<String> existingDomains = existing.stream()
        .map(MarketplaceSyncStateEntity::getDataDomain)
        .collect(Collectors.toSet());

    int created = 0;
    for (DataDomain domain : DataDomain.values()) {
      if (existingDomains.contains(domain.name())) {
        continue;
      }
      MarketplaceSyncStateEntity syncState = new MarketplaceSyncStateEntity();
      syncState.setMarketplaceConnectionId(connection.getId());
      syncState.setDataDomain(domain.name());
      syncState.setStatus(SyncStatus.IDLE.name());
      syncStateRepository.save(syncState);
      created++;
    }
    log.info("Sync states created: connectionId={}, created={}, alreadyExisted={}",
        connection.getId(), created, existingDomains.size());
  }
}
