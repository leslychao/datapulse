package io.datapulse.integration.domain;

import io.datapulse.integration.api.ValidateConnectionResponse;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionValidationServiceTest {

  @Mock
  private MarketplaceConnectionRepository connectionRepository;
  @Mock
  private SecretReferenceRepository secretReferenceRepository;
  @Mock
  private CredentialStore credentialStore;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private List<MarketplaceHealthProbe> healthProbes;
  @Mock
  private ConnectionValidationResultApplier resultApplier;

  @InjectMocks
  private ConnectionValidationService validationService;

  private MarketplaceConnectionEntity buildConnection(String marketplace) {
    var conn = new MarketplaceConnectionEntity();
    conn.setId(1L);
    conn.setWorkspaceId(1L);
    conn.setMarketplaceType(marketplace);
    conn.setSecretReferenceId(100L);
    conn.setStatus(ConnectionStatus.PENDING_VALIDATION.name());
    return conn;
  }

  @Nested
  @DisplayName("validateSync")
  class ValidateSync {

    @Test
    @DisplayName("should_return_valid_true_when_probe_succeeds")
    void should_return_valid_true_when_probe_succeeds() {
      var conn = buildConnection("WB");
      var secretRef = new SecretReferenceEntity();
      secretRef.setVaultPath("path");
      secretRef.setVaultKey("key");
      when(secretReferenceRepository.findById(100L))
          .thenReturn(Optional.of(secretRef));
      when(credentialStore.read("path", "key"))
          .thenReturn(Map.of("apiToken", "tok"));

      MarketplaceHealthProbe probe = new MarketplaceHealthProbe() {
        @Override
        public MarketplaceType marketplaceType() {
          return MarketplaceType.WB;
        }

        @Override
        public HealthProbeResult probe(Map<String, String> credentials) {
          return HealthProbeResult.success("ext-123");
        }
      };

      var service = new ConnectionValidationService(
          connectionRepository,
          secretReferenceRepository,
          credentialStore,
          eventPublisher,
          List.of(probe),
          resultApplier);

      ValidateConnectionResponse result = service.validateSync(conn);

      assertThat(result.valid()).isTrue();
      assertThat(result.errorCode()).isNull();
    }

    @Test
    @DisplayName("should_return_valid_false_when_probe_fails")
    void should_return_valid_false_when_probe_fails() {
      var conn = buildConnection("WB");
      var secretRef = new SecretReferenceEntity();
      secretRef.setVaultPath("path");
      secretRef.setVaultKey("key");
      when(secretReferenceRepository.findById(100L))
          .thenReturn(Optional.of(secretRef));
      when(credentialStore.read("path", "key"))
          .thenReturn(Map.of("apiToken", "tok"));

      MarketplaceHealthProbe probe = new MarketplaceHealthProbe() {
        @Override
        public MarketplaceType marketplaceType() {
          return MarketplaceType.WB;
        }

        @Override
        public HealthProbeResult probe(Map<String, String> credentials) {
          return HealthProbeResult.failure("AUTH_FAILED");
        }
      };

      var service = new ConnectionValidationService(
          connectionRepository,
          secretReferenceRepository,
          credentialStore,
          eventPublisher,
          List.of(probe),
          resultApplier);

      ValidateConnectionResponse result = service.validateSync(conn);

      assertThat(result.valid()).isFalse();
      assertThat(result.errorCode()).isEqualTo("AUTH_FAILED");
    }

    @Test
    @DisplayName("should_throw_when_no_probe_for_marketplace")
    void should_throw_when_no_probe_for_marketplace() {
      var conn = buildConnection("OZON");
      var secretRef = new SecretReferenceEntity();
      secretRef.setVaultPath("path");
      secretRef.setVaultKey("key");
      when(secretReferenceRepository.findById(100L))
          .thenReturn(Optional.of(secretRef));
      when(credentialStore.read("path", "key"))
          .thenReturn(Map.of("clientId", "c", "apiKey", "k"));

      MarketplaceHealthProbe wbProbe = new MarketplaceHealthProbe() {
        @Override
        public MarketplaceType marketplaceType() {
          return MarketplaceType.WB;
        }

        @Override
        public HealthProbeResult probe(Map<String, String> credentials) {
          return HealthProbeResult.success(null);
        }
      };

      var service = new ConnectionValidationService(
          connectionRepository,
          secretReferenceRepository,
          credentialStore,
          eventPublisher,
          List.of(wbProbe),
          resultApplier);

      assertThatThrownBy(() -> service.validateSync(conn))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No health probe for OZON");
    }
  }
}
