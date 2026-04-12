package io.datapulse.execution.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.execution.persistence.OfferConnectionResolver;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
    private final ObjectMapper objectMapper;

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

        if (row.marketplaceType() == MarketplaceType.YANDEX) {
            credentials = enrichWithYandexMetadata(credentials, row.connectionMetadata());
        }

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
                credentials,
                row.connectionMetadata()
        );
    }

    private Map<String, String> enrichWithYandexMetadata(
        Map<String, String> credentials, String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return credentials;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode businessNode = root.get(CredentialKeys.YANDEX_BUSINESS_ID);
            if (businessNode != null && !businessNode.isNull()) {
                var enriched = new HashMap<>(credentials);
                enriched.put(CredentialKeys.YANDEX_BUSINESS_ID,
                    String.valueOf(businessNode.asLong()));
                return Map.copyOf(enriched);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Yandex connection metadata: {}", e.getMessage());
        }
        return credentials;
    }
}
