package io.datapulse.integration.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.datapulse.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialMapperTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Nested
  @DisplayName("toVaultMap — WB")
  class ToVaultMapWb {

    @Test
    @DisplayName("should_return_map_with_api_token_when_valid_wb_credentials")
    void should_return_map_with_api_token_when_valid_wb_credentials() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("apiToken", "wb-test-token-123");

      Map<String, String> result = CredentialMapper.toVaultMap(MarketplaceType.WB, node);

      assertThat(result).containsEntry("apiToken", "wb-test-token-123");
      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("should_throw_when_api_token_missing")
    void should_throw_when_api_token_missing() {
      ObjectNode node = MAPPER.createObjectNode();

      assertThatThrownBy(() -> CredentialMapper.toVaultMap(MarketplaceType.WB, node))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("credentials.invalid");
    }

    @Test
    @DisplayName("should_throw_when_api_token_blank")
    void should_throw_when_api_token_blank() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("apiToken", "   ");

      assertThatThrownBy(() -> CredentialMapper.toVaultMap(MarketplaceType.WB, node))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("credentials.invalid");
    }

    @Test
    @DisplayName("should_trim_api_token_when_whitespace_present")
    void should_trim_api_token_when_whitespace_present() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("apiToken", "  token-value  ");

      Map<String, String> result = CredentialMapper.toVaultMap(MarketplaceType.WB, node);

      assertThat(result.get("apiToken")).isEqualTo("token-value");
    }
  }

  @Nested
  @DisplayName("toVaultMap — OZON")
  class ToVaultMapOzon {

    @Test
    @DisplayName("should_return_map_with_client_id_and_api_key_when_valid")
    void should_return_map_with_client_id_and_api_key_when_valid() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("clientId", "12345");
      node.put("apiKey", "ozon-secret");

      Map<String, String> result = CredentialMapper.toVaultMap(MarketplaceType.OZON, node);

      assertThat(result)
          .containsEntry("clientId", "12345")
          .containsEntry("apiKey", "ozon-secret");
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("should_throw_when_client_id_missing")
    void should_throw_when_client_id_missing() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("apiKey", "key");

      assertThatThrownBy(() -> CredentialMapper.toVaultMap(MarketplaceType.OZON, node))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("credentials.invalid");
    }

    @Test
    @DisplayName("should_throw_when_api_key_missing")
    void should_throw_when_api_key_missing() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("clientId", "12345");

      assertThatThrownBy(() -> CredentialMapper.toVaultMap(MarketplaceType.OZON, node))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("credentials.invalid");
    }
  }

  @Nested
  @DisplayName("parseOzonPerformanceCredentials")
  class ParsePerformance {

    @Test
    @DisplayName("should_parse_when_valid")
    void should_parse_when_valid() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("performanceClientId", "perf-id");
      node.put("performanceClientSecret", "perf-secret");

      OzonPerformanceCredentials creds =
          CredentialMapper.parseOzonPerformanceCredentials(node);

      assertThat(creds.performanceClientId()).isEqualTo("perf-id");
      assertThat(creds.performanceClientSecret()).isEqualTo("perf-secret");
    }

    @Test
    @DisplayName("should_throw_when_performance_client_id_missing")
    void should_throw_when_performance_client_id_missing() {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("performanceClientSecret", "secret");

      assertThatThrownBy(() -> CredentialMapper.parseOzonPerformanceCredentials(node))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("toPerformanceMap")
  class ToPerformanceMap {

    @Test
    @DisplayName("should_return_map_with_performance_fields")
    void should_return_map_with_performance_fields() {
      var creds = new OzonPerformanceCredentials("pid", "psecret");

      Map<String, String> result = CredentialMapper.toPerformanceMap(creds);

      assertThat(result)
          .containsEntry("performanceClientId", "pid")
          .containsEntry("performanceClientSecret", "psecret");
    }
  }

  @Nested
  @DisplayName("resolveSecretType")
  class ResolveSecretType {

    @Test
    @DisplayName("should_return_wb_api_token_for_wb")
    void should_return_wb_api_token_for_wb() {
      assertThat(CredentialMapper.resolveSecretType(MarketplaceType.WB))
          .isEqualTo(SecretType.WB_API_TOKEN);
    }

    @Test
    @DisplayName("should_return_ozon_seller_credentials_for_ozon")
    void should_return_ozon_seller_credentials_for_ozon() {
      assertThat(CredentialMapper.resolveSecretType(MarketplaceType.OZON))
          .isEqualTo(SecretType.OZON_SELLER_CREDENTIALS);
    }
  }

  @Nested
  @DisplayName("extractRequired — edge cases")
  class ExtractRequired {

    @Test
    @DisplayName("should_throw_when_node_is_null")
    void should_throw_when_node_is_null() {
      assertThatThrownBy(() -> CredentialMapper.toVaultMap(MarketplaceType.WB, null))
          .isInstanceOf(BadRequestException.class);
    }
  }
}
