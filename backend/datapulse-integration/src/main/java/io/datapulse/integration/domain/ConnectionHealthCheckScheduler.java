package io.datapulse.integration.domain;

import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.event.ConnectionHealthDegradedEvent;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionHealthCheckScheduler {

    private static final long ACQUIRE_TIMEOUT_SECONDS = 120;

    private final MarketplaceConnectionRepository connectionRepository;
    private final SecretReferenceRepository secretReferenceRepository;
    private final CredentialStore credentialStore;
    private final MarketplaceRateLimiter rateLimiter;
    private final IntegrationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final WebClient.Builder webClientBuilder;

    private final ConcurrentHashMap<Long, AtomicInteger> failureCounts = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${datapulse.integration.health-check.interval:PT15M}")
    public void checkActiveConnections() {
        List<MarketplaceConnectionEntity> activeConnections =
                connectionRepository.findAllByStatus(ConnectionStatus.ACTIVE.name());

        log.info("Health-check started: activeConnections={}", activeConnections.size());

        for (MarketplaceConnectionEntity connection : activeConnections) {
            try {
                checkSingleConnection(connection);
            } catch (Exception e) {
                log.error("Health-check error: connectionId={}", connection.getId(), e);
            }
        }

        cleanupStaleFailureCounts(activeConnections);
    }

    private void checkSingleConnection(MarketplaceConnectionEntity connection) {
        MarketplaceType marketplaceType = MarketplaceType.valueOf(connection.getMarketplaceType());
        RateLimitGroup healthCheckGroup = resolveHealthCheckGroup(marketplaceType);

        try {
            rateLimiter.acquire(connection.getId(), healthCheckGroup)
                    .get(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Health-check rate limit acquire timeout: connectionId={}", connection.getId());
            return;
        }

        Map<String, String> credentials;
        try {
            SecretReferenceEntity secretRef = secretReferenceRepository
                    .findById(connection.getSecretReferenceId())
                    .orElse(null);
            if (secretRef == null) {
                log.warn("Health-check skipped, secret reference not found: connectionId={}", connection.getId());
                return;
            }
            credentials = credentialStore.read(secretRef.getVaultPath(), secretRef.getVaultKey());
        } catch (Exception e) {
            log.warn("Health-check skipped, Vault unavailable: connectionId={}", connection.getId());
            return;
        }

        String errorCode = performHealthCall(connection, marketplaceType, credentials);

        if (errorCode == null) {
            onHealthCheckSuccess(connection);
            rateLimiter.onResponse(connection.getId(), healthCheckGroup, 200);
        } else {
            onHealthCheckFailure(connection, errorCode, marketplaceType);
            int httpStatus = errorCode.startsWith("HTTP_") ? parseHttpStatus(errorCode) : 401;
            rateLimiter.onResponse(connection.getId(), healthCheckGroup, httpStatus);
        }
    }

    private String performHealthCall(MarketplaceConnectionEntity connection,
                                     MarketplaceType marketplaceType,
                                     Map<String, String> credentials) {
        return switch (marketplaceType) {
            case WB -> callWbHealthCheck(credentials);
            case OZON -> callOzonHealthCheck(credentials, marketplaceType);
        };
    }

    private String callWbHealthCheck(Map<String, String> credentials) {
        String apiToken = credentials.get("apiToken");
        String baseUrl = properties.getWildberries().getContentBaseUrl();
        try {
            webClientBuilder.baseUrl(baseUrl).build()
                    .post()
                    .uri("/content/v2/get/cards/list")
                    .header("Authorization", apiToken)
                    .bodyValue(Map.of("settings", Map.of("cursor", Map.of("limit", 1))))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return null;
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            return "AUTH_FAILED";
        } catch (WebClientResponseException e) {
            return "HTTP_" + e.getStatusCode().value();
        } catch (Exception e) {
            return "CONNECTION_ERROR";
        }
    }

    private String callOzonHealthCheck(Map<String, String> credentials, MarketplaceType type) {
        String clientId = credentials.get("clientId");
        String apiKey = credentials.get("apiKey");
        String baseUrl = properties.getOzon().getSellerBaseUrl();
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
            return null;
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden e) {
            return "AUTH_FAILED";
        } catch (WebClientResponseException e) {
            return "HTTP_" + e.getStatusCode().value();
        } catch (Exception e) {
            return "CONNECTION_ERROR";
        }
    }

    @Transactional
    protected void onHealthCheckSuccess(MarketplaceConnectionEntity connection) {
        connection = connectionRepository.findById(connection.getId()).orElse(null);
        if (connection == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        connection.setLastCheckAt(now);
        connection.setLastSuccessAt(now);
        connectionRepository.save(connection);

        failureCounts.remove(connection.getId());

        log.debug("Health-check success: connectionId={}", connection.getId());
    }

    @Transactional
    protected void onHealthCheckFailure(MarketplaceConnectionEntity connection, String errorCode,
                                        MarketplaceType marketplaceType) {
        connection = connectionRepository.findById(connection.getId()).orElse(null);
        if (connection == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        connection.setLastCheckAt(now);
        connection.setLastErrorAt(now);
        connection.setLastErrorCode(errorCode);

        AtomicInteger counter = failureCounts.computeIfAbsent(connection.getId(), k -> new AtomicInteger(0));
        int failures = counter.incrementAndGet();
        int threshold = properties.getHealthCheck().getFailureThreshold();

        log.warn("Health-check failure: connectionId={}, errorCode={}, consecutiveFailures={}/{}",
                connection.getId(), errorCode, failures, threshold);

        if (failures >= threshold) {
            String oldStatus = connection.getStatus();
            connection.setStatus(ConnectionStatus.AUTH_FAILED.name());
            connectionRepository.save(connection);

            failureCounts.remove(connection.getId());

            eventPublisher.publishEvent(new ConnectionHealthDegradedEvent(
                    connection.getId(),
                    connection.getWorkspaceId(),
                    marketplaceType.name(),
                    failures,
                    errorCode));

            eventPublisher.publishEvent(new ConnectionStatusChangedEvent(
                    connection.getId(),
                    oldStatus,
                    ConnectionStatus.AUTH_FAILED.name(),
                    "health_check_failure"));

            log.warn("Health-check threshold reached, connection degraded: connectionId={}, failures={}",
                    connection.getId(), failures);
        } else {
            connectionRepository.save(connection);
        }
    }

    private RateLimitGroup resolveHealthCheckGroup(MarketplaceType marketplaceType) {
        return switch (marketplaceType) {
            case WB -> RateLimitGroup.WB_CONTENT;
            case OZON -> RateLimitGroup.OZON_DEFAULT;
        };
    }

    private void cleanupStaleFailureCounts(List<MarketplaceConnectionEntity> activeConnections) {
        var activeIds = activeConnections.stream()
                .map(MarketplaceConnectionEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        failureCounts.keySet().removeIf(id -> !activeIds.contains(id));
    }

    private int parseHttpStatus(String errorCode) {
        try {
            return Integer.parseInt(errorCode.substring(5));
        } catch (NumberFormatException e) {
            return 500;
        }
    }
}
