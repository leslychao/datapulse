package io.datapulse.integration.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.api.ValidateConnectionResponse;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionValidationService {

    private final MarketplaceConnectionRepository connectionRepository;
    private final SecretReferenceRepository secretReferenceRepository;
    private final MarketplaceSyncStateRepository syncStateRepository;
    private final CredentialStore credentialStore;
    private final ApplicationEventPublisher eventPublisher;
    private final WebClient.Builder webClientBuilder;
    private final IntegrationProperties integrationProperties;

    @Async("integrationExecutor")
    public void validateAsync(Long connectionId) {
        log.info("Async validation started: connectionId={}", connectionId);
        try {
            MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
                    .orElseThrow(() -> NotFoundException.connection(connectionId));

            ValidationResult result = performValidation(connection);

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
        ValidationResult result = performValidation(connection);
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

    private ValidationResult performValidation(MarketplaceConnectionEntity connection) {
        SecretReferenceEntity secretRef = secretReferenceRepository.findById(connection.getSecretReferenceId())
                .orElseThrow(() -> NotFoundException.entity("SecretReference", connection.getSecretReferenceId()));

        Map<String, String> credentials = credentialStore.read(secretRef.getVaultPath(), secretRef.getVaultKey());
        MarketplaceType marketplaceType = MarketplaceType.valueOf(connection.getMarketplaceType());

        return switch (marketplaceType) {
            case WB -> validateWb(credentials);
            case OZON -> validateOzon(credentials);
        };
    }

    private ValidationResult validateWb(Map<String, String> credentials) {
        String apiToken = credentials.get("apiToken");
        String baseUrl = integrationProperties.getWildberries().getContentBaseUrl();
        try {
            webClientBuilder.baseUrl(baseUrl).build()
                    .post()
                    .uri("/content/v2/get/cards/list")
                    .header("Authorization", apiToken)
                    .bodyValue(Map.of(
                            "settings", Map.of("cursor", Map.of("limit", 1))))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ValidationResult.success(null);
        } catch (WebClientResponseException.Unauthorized e) {
            return ValidationResult.failure("AUTH_FAILED");
        } catch (WebClientResponseException.Forbidden e) {
            return ValidationResult.failure("AUTH_FAILED");
        } catch (WebClientResponseException e) {
            log.warn("WB validation unexpected status: status={}", e.getStatusCode().value());
            return ValidationResult.failure("HTTP_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("WB validation error", e);
            return ValidationResult.failure("CONNECTION_ERROR");
        }
    }

    private ValidationResult validateOzon(Map<String, String> credentials) {
        String clientId = credentials.get("clientId");
        String apiKey = credentials.get("apiKey");
        String baseUrl = integrationProperties.getOzon().getSellerBaseUrl();
        try {
            webClientBuilder.baseUrl(baseUrl).build()
                    .post()
                    .uri("/v3/product/list")
                    .header("Client-Id", clientId)
                    .header("Api-Key", apiKey)
                    .bodyValue(Map.of("filter", Map.of(), "limit", 1))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ValidationResult.success(clientId);
        } catch (WebClientResponseException.Unauthorized e) {
            return ValidationResult.failure("AUTH_FAILED");
        } catch (WebClientResponseException.Forbidden e) {
            return ValidationResult.failure("AUTH_FAILED");
        } catch (WebClientResponseException e) {
            log.warn("Ozon validation unexpected status: status={}", e.getStatusCode().value());
            return ValidationResult.failure("HTTP_" + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Ozon validation error", e);
            return ValidationResult.failure("CONNECTION_ERROR");
        }
    }

    private void createSyncStatesForConnection(MarketplaceConnectionEntity connection) {
        for (DataDomain domain : DataDomain.values()) {
            MarketplaceSyncStateEntity syncState = new MarketplaceSyncStateEntity();
            syncState.setMarketplaceConnectionId(connection.getId());
            syncState.setDataDomain(domain.name());
            syncState.setStatus(SyncStatus.IDLE.name());
            syncStateRepository.save(syncState);
        }
        log.info("Sync states created: connectionId={}, domains={}",
                connection.getId(), DataDomain.values().length);
    }

    private record ValidationResult(boolean success, String externalAccountId, String errorCode) {
        static ValidationResult success(String externalAccountId) {
            return new ValidationResult(true, externalAccountId, null);
        }

        static ValidationResult failure(String errorCode) {
            return new ValidationResult(false, null, errorCode);
        }
    }
}
