package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.datapulse.execution.persistence.OfferConnectionResolver;
import io.datapulse.execution.persistence.OfferConnectionResolver.OfferConnectionRow;
import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.domain.MarketplaceType;
import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionCredentialResolver")
class ExecutionCredentialResolverTest {

  @Mock private OfferConnectionResolver offerConnectionResolver;
  @Mock private SecretReferenceRepository secretReferenceRepository;
  @Mock private CredentialStore credentialStore;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ExecutionCredentialResolver resolver;

  @Nested
  @DisplayName("resolve")
  class Resolve {

    @Test
    @DisplayName("should resolve full execution context")
    void should_resolveContext_when_allDataPresent() {
      var row = offerRow();
      var secretRef = secretRefEntity();
      var credentials = Map.of("token", "test-token");

      when(offerConnectionResolver.resolve(100L)).thenReturn(Optional.of(row));
      when(secretReferenceRepository.findById(7L)).thenReturn(Optional.of(secretRef));
      when(credentialStore.read("vault/path", "vault-key")).thenReturn(credentials);

      OfferExecutionContext ctx = resolver.resolve(100L);

      assertThat(ctx.offerId()).isEqualTo(100L);
      assertThat(ctx.connectionId()).isEqualTo(5L);
      assertThat(ctx.workspaceId()).isEqualTo(10L);
      assertThat(ctx.marketplaceType()).isEqualTo(MarketplaceType.WB);
      assertThat(ctx.marketplaceSku()).isEqualTo("SKU-123");
      assertThat(ctx.credentials()).containsEntry("token", "test-token");

      verify(eventPublisher).publishEvent(any(CredentialAccessedEvent.class));
    }

    @Test
    @DisplayName("should throw when offer not found")
    void should_throwIllegalState_when_offerNotFound() {
      when(offerConnectionResolver.resolve(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> resolver.resolve(999L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Marketplace offer not found");
    }

    @Test
    @DisplayName("should throw when secret reference not found")
    void should_throwIllegalState_when_secretRefNotFound() {
      when(offerConnectionResolver.resolve(100L))
          .thenReturn(Optional.of(offerRow()));
      when(secretReferenceRepository.findById(7L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> resolver.resolve(100L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SecretReference not found");
    }
  }

  private OfferConnectionRow offerRow() {
    return new OfferConnectionRow(
        100L, 5L, 10L, MarketplaceType.WB,
        "SKU-123", null, 7L);
  }

  private SecretReferenceEntity secretRefEntity() {
    var entity = new SecretReferenceEntity();
    entity.setId(7L);
    entity.setVaultPath("vault/path");
    entity.setVaultKey("vault-key");
    return entity;
  }
}
