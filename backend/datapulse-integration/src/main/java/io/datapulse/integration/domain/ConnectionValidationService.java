package io.datapulse.integration.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.api.ValidateConnectionResponse;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionValidationService {

    private final MarketplaceConnectionRepository connectionRepository;
    private final SecretReferenceRepository secretReferenceRepository;
    private final CredentialStore credentialStore;
    private final ApplicationEventPublisher eventPublisher;
    private final List<MarketplaceHealthProbe> healthProbes;
    private final ConnectionValidationResultApplier resultApplier;

    @Async("integrationExecutor")
    public void validateAsync(Long connectionId) {
        log.info("Async validation started: connectionId={}", connectionId);
        try {
            MarketplaceConnectionEntity connection = connectionRepository.findById(connectionId)
                    .orElseThrow(() -> NotFoundException.connection(connectionId));

            HealthProbeResult result = performValidation(connection);

            if (result.success()) {
                resultApplier.applySuccess(connectionId, result.externalAccountId());
                log.info("Async validation success: connectionId={}, externalAccountId={}",
                        connectionId, result.externalAccountId());
            } else {
                resultApplier.applyFailure(connectionId, result.errorCode());
                log.warn("Async validation failed: connectionId={}, error={}",
                        connectionId, result.errorCode());
            }
        } catch (Exception e) {
            log.error("Async validation error: connectionId={}", connectionId, e);
            resultApplier.applyFailure(connectionId, "VALIDATION_ERROR");
        }
    }

    public ValidateConnectionResponse validateSync(MarketplaceConnectionEntity connection) {
        HealthProbeResult result = performValidation(connection);
        if (result.success()) {
            return new ValidateConnectionResponse(true, null);
        }
        return new ValidateConnectionResponse(false, result.errorCode());
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

}
