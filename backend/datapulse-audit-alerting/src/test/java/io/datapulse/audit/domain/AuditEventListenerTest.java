package io.datapulse.audit.domain;

import io.datapulse.audit.persistence.AuditLogRepository;
import io.datapulse.platform.audit.AuditEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  @InjectMocks
  private AuditEventListener listener;

  @Nested
  @DisplayName("onAuditEvent")
  class OnAuditEvent {

    @Test
    @DisplayName("should_insert_audit_log_when_event_received")
    void should_insert_audit_log_when_event_received() {
      var event = new AuditEvent(
          1L, "USER", 10L, "workspace.create",
          "workspace", "5", "SUCCESS",
          "{\"name\":\"Test\"}", "127.0.0.1", "corr-id");

      listener.onAuditEvent(event);

      verify(auditLogRepository).insert(
          1L, "USER", 10L, "workspace.create",
          "workspace", "5", "SUCCESS",
          "{\"name\":\"Test\"}", "127.0.0.1", "corr-id");
    }

    @Test
    @DisplayName("should_handle_null_optional_fields_when_system_event")
    void should_handle_null_optional_fields_when_system_event() {
      var event = new AuditEvent(
          null, "SYSTEM", null, "user.provision",
          "app_user", "1", "SUCCESS",
          null, null, null);

      listener.onAuditEvent(event);

      verify(auditLogRepository).insert(
          null, "SYSTEM", null, "user.provision",
          "app_user", "1", "SUCCESS",
          null, null, null);
    }

    @Test
    @DisplayName("should_not_throw_when_repository_fails")
    void should_not_throw_when_repository_fails() {
      var event = new AuditEvent(
          1L, "USER", 10L, "test.action",
          "entity", "1", "SUCCESS",
          null, null, null);

      doThrow(new RuntimeException("DB down"))
          .when(auditLogRepository).insert(
              any(), any(), any(), any(), any(), any(),
              any(), any(), any(), any());

      assertThatCode(() -> listener.onAuditEvent(event))
          .doesNotThrowAnyException();
    }
  }
}
