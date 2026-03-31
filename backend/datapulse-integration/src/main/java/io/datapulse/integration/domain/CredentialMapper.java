package io.datapulse.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.datapulse.common.exception.BadRequestException;

import java.util.HashMap;
import java.util.Map;

public final class CredentialMapper {

    private CredentialMapper() {
    }

    public static Map<String, String> toVaultMap(MarketplaceType marketplaceType, JsonNode credentials) {
        return switch (marketplaceType) {
            case WB -> toWbMap(parseWbCredentials(credentials));
            case OZON -> toOzonMap(parseOzonSellerCredentials(credentials));
        };
    }

    public static WbCredentials parseWbCredentials(JsonNode node) {
        String apiToken = extractRequired(node, "apiToken");
        return new WbCredentials(apiToken);
    }

    public static OzonSellerCredentials parseOzonSellerCredentials(JsonNode node) {
        String clientId = extractRequired(node, "clientId");
        String apiKey = extractRequired(node, "apiKey");
        return new OzonSellerCredentials(clientId, apiKey);
    }

    public static OzonPerformanceCredentials parseOzonPerformanceCredentials(JsonNode node) {
        String clientId = extractRequired(node, "performanceClientId");
        String clientSecret = extractRequired(node, "performanceClientSecret");
        return new OzonPerformanceCredentials(clientId, clientSecret);
    }

    public static Map<String, String> toWbMap(WbCredentials creds) {
        Map<String, String> map = new HashMap<>();
        map.put("apiToken", creds.apiToken());
        return map;
    }

    public static Map<String, String> toOzonMap(OzonSellerCredentials creds) {
        Map<String, String> map = new HashMap<>();
        map.put("clientId", creds.clientId());
        map.put("apiKey", creds.apiKey());
        return map;
    }

    public static Map<String, String> toPerformanceMap(OzonPerformanceCredentials creds) {
        Map<String, String> map = new HashMap<>();
        map.put("performanceClientId", creds.performanceClientId());
        map.put("performanceClientSecret", creds.performanceClientSecret());
        return map;
    }

    public static SecretType resolveSecretType(MarketplaceType marketplaceType) {
        return switch (marketplaceType) {
            case WB -> SecretType.WB_API_TOKEN;
            case OZON -> SecretType.OZON_SELLER_CREDENTIALS;
        };
    }

    private static String extractRequired(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            throw BadRequestException.of("credentials.invalid", field);
        }
        String value = node.get(field).asText();
        if (value == null || value.isBlank()) {
            throw BadRequestException.of("credentials.invalid", field);
        }
        return value.trim();
    }
}
