package io.datapulse.etl.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Resolves marketplace API credentials for a given connection.
 * Reads secret reference from DB, fetches credentials from Vault (with cache),
 * returns raw credentials map.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialResolver {

    private final MarketplaceConnectionRepository connectionRepository;
    private final SecretReferenceRepository secretReferenceRepository;
    private final CredentialStore credentialStore;
    private final ApplicationEventPublisher eventPublisher;

    public ResolvedCredentials resolve(long connectionId) {
        MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> NotFoundException.connection(connectionId));

        return resolve(connection);
    }

    public ResolvedCredentials resolve(MarketplaceConnectionEntity connection) {
        SecretReferenceEntity secretRef = secretReferenceRepository
                .findById(connection.getSecretReferenceId())
                .orElseThrow(() -> new IllegalStateException(
                        "SecretReference not found: id=%d, connectionId=%d"
                                .formatted(connection.getSecretReferenceId(), connection.getId())));

        Map<String, String> credentials = credentialStore.read(
                secretRef.getVaultPath(), secretRef.getVaultKey());

        eventPublisher.publishEvent(new CredentialAccessedEvent(
                connection.getId(), connection.getWorkspaceId(), "etl_sync"));

        MarketplaceType marketplace = MarketplaceType.valueOf(connection.getMarketplaceType());

        log.debug("Credentials resolved: connectionId={}, marketplace={}",
                connection.getId(), marketplace);

        return new ResolvedCredentials(
                connection.getId(),
                connection.getWorkspaceId(),
                marketplace,
                credentials
        );
    }

    /**
     * Resolves Ozon Performance API credentials from {@code perfSecretReferenceId}.
     * Returns empty if the connection has no performance credentials configured.
     */
    public Optional<Map<String, String>> resolvePerformanceCredentials(long connectionId) {
        MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> NotFoundException.connection(connectionId));

        if (connection.getPerfSecretReferenceId() == null) {
            return Optional.empty();
        }

        SecretReferenceEntity perfSecretRef = secretReferenceRepository
                .findById(connection.getPerfSecretReferenceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Performance SecretReference not found: id=%d, connectionId=%d"
                                .formatted(connection.getPerfSecretReferenceId(),
                                        connection.getId())));

        Map<String, String> perfCredentials = credentialStore.read(
                perfSecretRef.getVaultPath(), perfSecretRef.getVaultKey());

        log.debug("Performance credentials resolved: connectionId={}", connectionId);
        return Optional.of(perfCredentials);
    }

    /**
     * Resolves base credentials merged with Ozon Performance credentials.
     * Returns empty if the connection has no performance credentials configured.
     */
    public Optional<Map<String, String>> resolveWithPerformanceCredentials(long connectionId) {
        MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> NotFoundException.connection(connectionId));

        if (connection.getPerfSecretReferenceId() == null) {
            return Optional.empty();
        }

        ResolvedCredentials base = resolve(connection);

        SecretReferenceEntity perfSecretRef = secretReferenceRepository
                .findById(connection.getPerfSecretReferenceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Performance SecretReference not found: id=%d, connectionId=%d"
                                .formatted(connection.getPerfSecretReferenceId(),
                                        connection.getId())));

        Map<String, String> perfCredentials = credentialStore.read(
                perfSecretRef.getVaultPath(), perfSecretRef.getVaultKey());

        Map<String, String> merged = new HashMap<>(base.credentials());
        merged.putAll(perfCredentials);

        log.debug("Credentials with performance merged: connectionId={}", connectionId);
        return Optional.of(merged);
    }

    public record ResolvedCredentials(
            long connectionId,
            long workspaceId,
            MarketplaceType marketplace,
            Map<String, String> credentials
    ) {}
}
