package io.datapulse.integration.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.api.CallLogFilter;
import io.datapulse.integration.api.CallLogResponse;
import io.datapulse.integration.api.ConnectionMapper;
import io.datapulse.integration.api.ConnectionResponse;
import io.datapulse.integration.api.ConnectionSummaryResponse;
import io.datapulse.integration.api.CreateConnectionRequest;
import io.datapulse.integration.api.SyncStateResponse;
import io.datapulse.integration.api.UpdateConnectionRequest;
import io.datapulse.integration.api.UpdateCredentialsRequest;
import io.datapulse.integration.api.UpdatePerformanceCredentialsRequest;
import io.datapulse.integration.api.ValidateConnectionResponse;
import io.datapulse.integration.domain.event.ConnectionCreatedEvent;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.domain.event.CredentialRotatedEvent;
import io.datapulse.integration.domain.event.SyncTriggeredEvent;
import io.datapulse.integration.persistence.IntegrationCallLogEntity;
import io.datapulse.integration.persistence.IntegrationCallLogRepository;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private static final String VAULT_PROVIDER = "vault";

    private final MarketplaceConnectionRepository connectionRepository;
    private final SecretReferenceRepository secretReferenceRepository;
    private final MarketplaceSyncStateRepository syncStateRepository;
    private final IntegrationCallLogRepository callLogRepository;
    private final CredentialStore credentialStore;
    private final ConnectionValidationService validationService;
    private final ConnectionMapper connectionMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConnectionResponse createConnection(CreateConnectionRequest request, Long workspaceId, Long userId) {
        MarketplaceType marketplaceType = request.marketplaceType();
        Map<String, String> credentialsMap = CredentialMapper.toVaultMap(marketplaceType, request.credentials());
        SecretType secretType = CredentialMapper.resolveSecretType(marketplaceType);

        String vaultPath = buildVaultPath(workspaceId, marketplaceType, "seller");
        String vaultKey = "credentials";

        int vaultVersion = credentialStore.store(vaultPath, vaultKey, credentialsMap);

        SecretReferenceEntity secretRef = createSecretReference(
                workspaceId, vaultPath, vaultKey, vaultVersion, secretType);

        MarketplaceConnectionEntity connection = new MarketplaceConnectionEntity();
        connection.setWorkspaceId(workspaceId);
        connection.setMarketplaceType(marketplaceType.name());
        connection.setName(request.name());
        connection.setStatus(ConnectionStatus.PENDING_VALIDATION.name());
        connection.setSecretReferenceId(secretRef.getId());
        connectionRepository.save(connection);

        log.info("Connection created: connectionId={}, workspaceId={}, marketplace={}",
                connection.getId(), workspaceId, marketplaceType);

        eventPublisher.publishEvent(new ConnectionCreatedEvent(
                connection.getId(), workspaceId, marketplaceType.name(), userId));

        List<MarketplaceSyncStateEntity> syncStates = List.of();
        return connectionMapper.toResponse(connection, syncStates);
    }

    @Transactional(readOnly = true)
    public List<ConnectionSummaryResponse> listConnections(Long workspaceId) {
        List<MarketplaceConnectionEntity> connections = connectionRepository.findAllByWorkspaceId(workspaceId);
        return connectionMapper.toSummaries(connections);
    }

    @Transactional(readOnly = true)
    public ConnectionResponse getConnection(Long connectionId, Long workspaceId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);
        List<MarketplaceSyncStateEntity> syncStates =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        return connectionMapper.toResponse(connection, syncStates);
    }

    @Transactional
    public ConnectionResponse updateConnection(Long connectionId, UpdateConnectionRequest request, Long workspaceId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);
        connection.setName(request.name());
        connectionRepository.save(connection);

        List<MarketplaceSyncStateEntity> syncStates =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        return connectionMapper.toResponse(connection, syncStates);
    }

    @Transactional
    public ConnectionResponse updateCredentials(Long connectionId, UpdateCredentialsRequest request,
                                                Long workspaceId, Long userId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);
        MarketplaceType marketplaceType = MarketplaceType.valueOf(connection.getMarketplaceType());

        Map<String, String> credentialsMap = CredentialMapper.toVaultMap(marketplaceType, request.credentials());

        SecretReferenceEntity secretRef = secretReferenceRepository.findById(connection.getSecretReferenceId())
                .orElseThrow(() -> NotFoundException.entity("SecretReference", connection.getSecretReferenceId()));

        int newVersion = credentialStore.rotate(secretRef.getVaultPath(), secretRef.getVaultKey(), credentialsMap);
        secretRef.setVaultVersion(newVersion);
        secretRef.setRotatedAt(OffsetDateTime.now());
        secretReferenceRepository.save(secretRef);

        String oldStatus = connection.getStatus();
        connection.setStatus(ConnectionStatus.PENDING_VALIDATION.name());
        connection.setLastErrorCode(null);
        connection.setLastErrorAt(null);
        connectionRepository.save(connection);

        log.info("Credentials rotated: connectionId={}, workspaceId={}, newVaultVersion={}",
                connectionId, workspaceId, newVersion);

        eventPublisher.publishEvent(new CredentialRotatedEvent(connectionId, workspaceId, userId));
        eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
                connectionId, oldStatus, ConnectionStatus.PENDING_VALIDATION.name(), "credential_rotation"));

        List<MarketplaceSyncStateEntity> syncStates =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        return connectionMapper.toResponse(connection, syncStates);
    }

    @Transactional
    public ConnectionResponse updatePerformanceCredentials(Long connectionId,
                                                           UpdatePerformanceCredentialsRequest request,
                                                           Long workspaceId, Long userId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);

        if (!MarketplaceType.OZON.name().equals(connection.getMarketplaceType())) {
            throw BadRequestException.of("connection.marketplace.mismatch", "OZON", connection.getMarketplaceType());
        }

        OzonPerformanceCredentials creds = new OzonPerformanceCredentials(
                request.performanceClientId(), request.performanceClientSecret());
        Map<String, String> credentialsMap = CredentialMapper.toPerformanceMap(creds);

        String vaultPath = buildVaultPath(workspaceId, MarketplaceType.OZON, "performance");
        String vaultKey = "credentials";

        if (connection.getPerfSecretReferenceId() != null) {
            SecretReferenceEntity existing = secretReferenceRepository
                    .findById(connection.getPerfSecretReferenceId())
                    .orElseThrow(() -> NotFoundException.entity(
                            "SecretReference", connection.getPerfSecretReferenceId()));

            int newVersion = credentialStore.rotate(existing.getVaultPath(), existing.getVaultKey(), credentialsMap);
            existing.setVaultVersion(newVersion);
            existing.setRotatedAt(OffsetDateTime.now());
            secretReferenceRepository.save(existing);
        } else {
            int vaultVersion = credentialStore.store(vaultPath, vaultKey, credentialsMap);
            SecretReferenceEntity perfRef = createSecretReference(
                    workspaceId, vaultPath, vaultKey, vaultVersion, SecretType.OZON_PERFORMANCE_OAUTH2);
            connection.setPerfSecretReferenceId(perfRef.getId());
        }

        connectionRepository.save(connection);

        log.info("Performance credentials updated: connectionId={}, workspaceId={}", connectionId, workspaceId);
        eventPublisher.publishEvent(new CredentialRotatedEvent(connectionId, workspaceId, userId));

        List<MarketplaceSyncStateEntity> syncStates =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        return connectionMapper.toResponse(connection, syncStates);
    }

    @Transactional(readOnly = true)
    public ValidateConnectionResponse validateConnection(Long connectionId, Long workspaceId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);
        return validationService.validateSync(connection);
    }

    @Transactional
    public void disableConnection(Long connectionId, Long workspaceId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);
        ensureNotTerminal(connection);

        String oldStatus = connection.getStatus();
        connection.setStatus(ConnectionStatus.DISABLED.name());
        connectionRepository.save(connection);

        log.info("Connection disabled: connectionId={}, workspaceId={}", connectionId, workspaceId);
        eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
                connectionId, oldStatus, ConnectionStatus.DISABLED.name(), "admin_disable"));
    }

    @Transactional
    public void enableConnection(Long connectionId, Long workspaceId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);

        if (!ConnectionStatus.DISABLED.name().equals(connection.getStatus())) {
            throw BadRequestException.of("connection.invalid.state", connection.getStatus(), "DISABLED");
        }

        String oldStatus = connection.getStatus();
        connection.setStatus(ConnectionStatus.PENDING_VALIDATION.name());
        connectionRepository.save(connection);

        log.info("Connection re-enabled: connectionId={}, workspaceId={}", connectionId, workspaceId);
        eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
                connectionId, oldStatus, ConnectionStatus.PENDING_VALIDATION.name(), "admin_enable"));
    }

    @Transactional
    public void archiveConnection(Long connectionId, Long workspaceId) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);
        ensureNotTerminal(connection);

        String oldStatus = connection.getStatus();
        connection.setStatus(ConnectionStatus.ARCHIVED.name());
        connectionRepository.save(connection);

        log.info("Connection archived: connectionId={}, workspaceId={}", connectionId, workspaceId);
        eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
                connectionId, oldStatus, ConnectionStatus.ARCHIVED.name(), "admin_archive"));
    }

    @Transactional(readOnly = true)
    public List<SyncStateResponse> getSyncStates(Long connectionId, Long workspaceId) {
        findConnectionOrThrow(connectionId, workspaceId);
        List<MarketplaceSyncStateEntity> syncStates =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        return connectionMapper.toSyncStates(syncStates);
    }

    @Transactional
    public void triggerSync(Long connectionId, Long workspaceId, Long userId,
                            io.datapulse.integration.api.TriggerSyncRequest request) {
        MarketplaceConnectionEntity connection = findConnectionOrThrow(connectionId, workspaceId);

        if (!ConnectionStatus.ACTIVE.name().equals(connection.getStatus())) {
            throw BadRequestException.of("connection.sync.requires.active", connection.getStatus());
        }

        List<String> domains = request != null ? request.domains() : null;

        log.info("Manual sync triggered: connectionId={}, workspaceId={}, userId={}, domains={}",
                connectionId, workspaceId, userId, domains);
        eventPublisher.publishEvent(new SyncTriggeredEvent(connectionId, workspaceId, userId, domains));
    }

    @Transactional(readOnly = true)
    public Page<CallLogResponse> getCallLog(Long connectionId, Long workspaceId,
                                            CallLogFilter filter, Pageable pageable) {
        findConnectionOrThrow(connectionId, workspaceId);
        Page<IntegrationCallLogEntity> page = callLogRepository.findByFilters(
                connectionId, filter.from(), filter.to(),
                filter.endpoint(), filter.httpStatus(), pageable);
        return page.map(this::toCallLogResponse);
    }

    private CallLogResponse toCallLogResponse(IntegrationCallLogEntity entity) {
        return new CallLogResponse(
                entity.getId(),
                entity.getEndpoint(),
                entity.getHttpMethod(),
                entity.getHttpStatus(),
                entity.getDurationMs(),
                entity.getRequestSizeBytes(),
                entity.getResponseSizeBytes(),
                entity.getCorrelationId(),
                entity.getErrorDetails(),
                entity.getRetryAttempt(),
                entity.getCreatedAt()
        );
    }

    private MarketplaceConnectionEntity findConnectionOrThrow(Long connectionId, Long workspaceId) {
        return connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId)
                .orElseThrow(() -> NotFoundException.connection(connectionId));
    }

    private void ensureNotTerminal(MarketplaceConnectionEntity connection) {
        ConnectionStatus current = ConnectionStatus.valueOf(connection.getStatus());
        if (current.isTerminal()) {
            throw BadRequestException.of("connection.invalid.state", connection.getStatus());
        }
    }

    private SecretReferenceEntity createSecretReference(Long workspaceId, String vaultPath,
                                                        String vaultKey, int vaultVersion,
                                                        SecretType secretType) {
        SecretReferenceEntity secretRef = new SecretReferenceEntity();
        secretRef.setWorkspaceId(workspaceId);
        secretRef.setProvider(VAULT_PROVIDER);
        secretRef.setVaultPath(vaultPath);
        secretRef.setVaultKey(vaultKey);
        secretRef.setVaultVersion(vaultVersion);
        secretRef.setSecretType(secretType.name());
        secretRef.setStatus(SecretStatus.ACTIVE.name());
        return secretReferenceRepository.save(secretRef);
    }

    private String buildVaultPath(Long workspaceId, MarketplaceType marketplace, String suffix) {
        return "datapulse/ws-%d/%s-%s".formatted(workspaceId, marketplace.name().toLowerCase(), suffix);
    }
}
