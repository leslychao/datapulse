package io.datapulse.execution.domain;

import io.datapulse.execution.persistence.OfferConnectionResolver;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resolves full execution context for a marketplace offer:
 * connection metadata + API credentials from Vault.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionCredentialResolver {

    private final OfferConnectionResolver offerConnectionResolver;
    private final SecretReferenceRepository secretReferenceRepository;
    private final CredentialStore credentialStore;
    private final ApplicationEventPublisher eventPublisher;

    public OfferExecutionContext resolve(long marketplaceOfferId) {
        var row = offerConnectionResolver.resolve(marketplaceOfferId)
                .orElseThrow(() -> new IllegalStateException(
                        "Marketplace offer not found: offerId=%d".formatted(marketplaceOfferId)));

        SecretReferenceEntity secretRef = secretReferenceRepository
                .findById(row.secretReferenceId())
                .orElseThrow(() -> new IllegalStateException(
                        "SecretReference not found: id=%d, connectionId=%d"
                                .formatted(row.secretReferenceId(), row.connectionId())));

        Map<String, String> credentials = credentialStore.read(
                secretRef.getVaultPath(), secretRef.getVaultKey());

        eventPublisher.publishEvent(new CredentialAccessedEvent(
                row.connectionId(), row.workspaceId(), "execution_write"));

        log.debug("Execution context resolved: offerId={}, connectionId={}, marketplace={}",
                marketplaceOfferId, row.connectionId(), row.marketplaceType());

        return new OfferExecutionContext(
                row.offerId(),
                row.connectionId(),
                row.workspaceId(),
                row.marketplaceType(),
                row.marketplaceSku(),
                row.marketplaceSkuAlt(),
                credentials
        );
    }
}
