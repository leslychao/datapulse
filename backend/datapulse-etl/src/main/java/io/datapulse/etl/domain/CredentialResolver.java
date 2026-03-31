package io.datapulse.etl.domain;

import java.util.Map;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public record ResolvedCredentials(
            long connectionId,
            long workspaceId,
            MarketplaceType marketplace,
            Map<String, String> credentials
    ) {}
}
