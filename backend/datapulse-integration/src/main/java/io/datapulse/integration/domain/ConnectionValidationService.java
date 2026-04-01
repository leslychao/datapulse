package io.datapulse.integration.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.api.ValidateConnectionResponse;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionValidationService {

    private final MarketplaceConnectionRepository connectionRepository;
    private final SecretReferenceRepository secretReferenceRepository;
    private final MarketplaceSyncStateRepository syncStateRepository;
    private final CredentialStore credentialStore;
    private final ApplicationEventPublisher eventPublisher;
    private final List<MarketplaceHealthProbe> healthProbes;

    @Async("integrationExecutor")
    public void validateAsync(Long connectionId) {
        log.info("Async validation started: connectionId={}", connectionId);
        try {
            MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
                    .orElseThrow(() -> NotFoundException.connection(connectionId));

            HealthProbeResult result = performValidation(connection);

            if (result.success()) {
                applyValidationSuccess(connectionId, result.externalAccountId());
                log.info("Async validation success: connectionId={}, externalAccountId={}",
                        connectionId, result.externalAccountId());
            } else {
                applyValidationFailure(connectionId, result.errorCode());
                log.warn("Async validation failed: connectionId={}, error={}",
                        connectionId, result.errorCode());
            }
        } catch (Exception e) {
            log.error("Async validation error: connectionId={}", connectionId, e);
            applyValidationFailure(connectionId, "VALIDATION_ERROR");
        }
    }

    public ValidateConnectionResponse validateSync(MarketplaceConnectionEntity connection) {
        HealthProbeResult result = performValidation(connection);
        if (result.success()) {
            return new ValidateConnectionResponse(true, null);
        }
        return new ValidateConnectionResponse(false, result.errorCode());
    }

    @Transactional
    protected void applyValidationSuccess(Long connectionId, String externalAccountId) {
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
    protected void applyValidationFailure(Long connectionId, String errorCode) {
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

    private HealthProbeResult performValidation(MarketplaceConnectionEntity connection) {
        SecretReferenceEntity secretRef = secretReferenceRepository.findById(connection.getSecretReferenceId())
                .orElseThrow(() -> NotFoundException.entity("SecretReference", connection.getSecretReferenceId()));

        Map<String, String> credentials = credentialStore.read(secretRef.getVaultPath(), secretRef.getVaultKey());

        eventPublisher.publishEvent(new CredentialAccessedEvent(
                connection.getId(), connection.getWorkspaceId(), "connection_validation"));

        MarketplaceType marketplaceType = MarketplaceType.valueOf(connection.getMarketplaceType());
        return resolveProbe(marketplaceType).probe(credentials);
    }

    private MarketplaceHealthProbe resolveProbe(MarketplaceType type) {
        return healthProbes.stream()
                .filter(p -> p.marketplaceType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No health probe for " + type));
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
