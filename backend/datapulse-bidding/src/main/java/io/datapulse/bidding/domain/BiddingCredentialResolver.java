package io.datapulse.bidding.domain;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves API credentials for marketplace connections within the bidding module.
 * Mirrors ExecutionCredentialResolver, including Yandex businessId enrichment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingCredentialResolver {

  private final MarketplaceConnectionRepository connectionRepository;
  private final SecretReferenceRepository secretReferenceRepository;
  private final CredentialStore credentialStore;
  private final ObjectMapper objectMapper;

  public Map<String, String> resolve(long connectionId) {
    MarketplaceConnectionEntity connection = connectionRepository
        .findById(connectionId)
        .orElseThrow(() -> new IllegalStateException(
            "Connection not found: connectionId=%d".formatted(connectionId)));

    SecretReferenceEntity secretRef = secretReferenceRepository
        .findById(connection.getSecretReferenceId())
        .orElseThrow(() -> new IllegalStateException(
            "SecretReference not found: id=%d, connectionId=%d"
                .formatted(connection.getSecretReferenceId(), connectionId)));

    Map<String, String> credentials = credentialStore.read(
        secretRef.getVaultPath(), secretRef.getVaultKey());

    if ("YANDEX".equals(connection.getMarketplaceType())) {
      credentials = enrichWithYandexMetadata(
          credentials, connection.getMetadata());
    }

    return credentials;
  }

  public String resolveMarketplaceType(long connectionId) {
    return connectionRepository.findById(connectionId)
        .map(MarketplaceConnectionEntity::getMarketplaceType)
        .orElse(null);
  }

  private Map<String, String> enrichWithYandexMetadata(
      Map<String, String> credentials, String metadata) {
    if (metadata == null || metadata.isBlank()) {
      return credentials;
    }
    try {
      JsonNode node = objectMapper.readTree(metadata);
      JsonNode businessIdNode = node.get("YANDEX_BUSINESS_ID");
      if (businessIdNode != null && !businessIdNode.isNull()) {
        var enriched = new HashMap<>(credentials);
        enriched.put(CredentialKeys.YANDEX_BUSINESS_ID,
            businessIdNode.asText());
        return enriched;
      }
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse connection metadata for Yandex businessId: "
          + "error={}", e.getMessage());
    }
    return credentials;
  }
}
