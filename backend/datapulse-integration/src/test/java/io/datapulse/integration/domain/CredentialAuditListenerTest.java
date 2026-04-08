package io.datapulse.integration.domain;

import io.datapulse.integration.domain.event.CredentialAccessedEvent;
import io.datapulse.integration.domain.event.CredentialRotatedEvent;
import io.datapulse.platform.audit.AuditPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CredentialAuditListenerTest {

  @Mock
  private AuditPublisher auditPublisher;

  @InjectMocks
  private CredentialAuditListener listener;

  @Nested
  @DisplayName("onCredentialRotated")
  class OnCredentialRotated {

    @Test
    @DisplayName("should publish audit event for credential rotation")
    void should_publish_audit() {
      var event = new CredentialRotatedEvent(10L, 1L, 42L);

      listener.onCredentialRotated(event);

      verify(auditPublisher).publish(
          "credential.rotate", "marketplace_connection", "10");
    }

    @Test
    @DisplayName("should not throw when audit fails")
    void should_not_throw_on_failure() {
      var event = new CredentialRotatedEvent(10L, 1L, 42L);

      doThrow(new RuntimeException("audit down"))
          .when(auditPublisher).publish(
              "credential.rotate", "marketplace_connection", "10");

      assertThatCode(() -> listener.onCredentialRotated(event))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("onCredentialAccessed")
  class OnCredentialAccessed {

    @Test
    @DisplayName("should publish system audit event for credential access")
    void should_publish_system_audit() {
      var event = new CredentialAccessedEvent(10L, 1L, "etl_sync");

      listener.onCredentialAccessed(event);

      verify(auditPublisher).publishSystemWithWorkspace(
          1L, "credential.access", "marketplace_connection", "10", "etl_sync");
    }

    @Test
    @DisplayName("should not throw when audit fails")
    void should_not_throw_on_failure() {
      var event = new CredentialAccessedEvent(10L, 1L, "health_check");

      doThrow(new RuntimeException("audit down"))
          .when(auditPublisher).publishSystemWithWorkspace(
              1L, "credential.access", "marketplace_connection", "10", "health_check");

      assertThatCode(() -> listener.onCredentialAccessed(event))
          .doesNotThrowAnyException();
    }
  }
}
