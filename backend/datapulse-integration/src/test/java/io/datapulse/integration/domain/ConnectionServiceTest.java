package io.datapulse.integration.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.integration.api.ConnectionMapper;
import io.datapulse.integration.api.ConnectionResponse;
import io.datapulse.integration.api.ConnectionSummaryResponse;
import io.datapulse.integration.api.CreateConnectionRequest;
import io.datapulse.integration.api.UpdateConnectionRequest;
import io.datapulse.integration.domain.event.ConnectionCreatedEvent;
import io.datapulse.integration.domain.event.ConnectionStatusChangedEvent;
import io.datapulse.integration.domain.event.SyncTriggeredEvent;
import io.datapulse.integration.persistence.IntegrationCallLogRepository;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.integration.persistence.SecretReferenceEntity;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  private MarketplaceConnectionRepository connectionRepository;
  @Mock
  private SecretReferenceRepository secretReferenceRepository;
  @Mock
  private MarketplaceSyncStateRepository syncStateRepository;
  @Mock
  private IntegrationCallLogRepository callLogRepository;
  @Mock
  private CredentialStore credentialStore;
  @Mock
  private ConnectionValidationService validationService;
  @Mock
  private ConnectionMapper connectionMapper;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ConnectionService connectionService;

  private MarketplaceConnectionEntity buildConnection(Long id, String marketplace, String status) {
    var conn = new MarketplaceConnectionEntity();
    conn.setId(id);
    conn.setWorkspaceId(1L);
    conn.setMarketplaceType(marketplace);
    conn.setName("Test Connection");
    conn.setStatus(status);
    conn.setSecretReferenceId(100L);
    return conn;
  }

  @Nested
  @DisplayName("createConnection")
  class CreateConnection {

    @Test
    @DisplayName("should_store_credentials_and_create_connection_when_valid_wb")
    void should_store_credentials_and_create_connection_when_valid_wb() {
      ObjectNode creds = MAPPER.createObjectNode();
      creds.put("apiToken", "test-token");

      when(credentialStore.store(anyString(), anyString(), any())).thenReturn(1);
      when(secretReferenceRepository.save(any(SecretReferenceEntity.class)))
          .thenAnswer(inv -> {
            SecretReferenceEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
          });
      when(connectionRepository.save(any(MarketplaceConnectionEntity.class)))
          .thenAnswer(inv -> {
            MarketplaceConnectionEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
          });
      when(connectionMapper.toResponse(any(), any())).thenReturn(null);

      connectionService.createConnection(
          new CreateConnectionRequest(MarketplaceType.WB, "My WB", creds),
          1L, 10L);

      verify(credentialStore).store(eq("datapulse/ws-1/wb-seller"), eq("credentials"), any());

      ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue()).isInstanceOf(ConnectionCreatedEvent.class);
    }

    @Test
    @DisplayName("should_store_credentials_with_ozon_vault_path_when_ozon")
    void should_store_credentials_with_ozon_vault_path_when_ozon() {
      ObjectNode creds = MAPPER.createObjectNode();
      creds.put("clientId", "123");
      creds.put("apiKey", "key");

      when(credentialStore.store(anyString(), anyString(), any())).thenReturn(1);
      when(secretReferenceRepository.save(any())).thenAnswer(inv -> {
        SecretReferenceEntity e = inv.getArgument(0);
        e.setId(100L);
        return e;
      });
      when(connectionRepository.save(any())).thenAnswer(inv -> {
        MarketplaceConnectionEntity e = inv.getArgument(0);
        e.setId(2L);
        return e;
      });
      when(connectionMapper.toResponse(any(), any())).thenReturn(null);

      connectionService.createConnection(
          new CreateConnectionRequest(MarketplaceType.OZON, "My Ozon", creds),
          5L, 10L);

      verify(credentialStore).store(eq("datapulse/ws-5/ozon-seller"), eq("credentials"), any());
    }
  }

  @Nested
  @DisplayName("disableConnection")
  class DisableConnection {

    @Test
    @DisplayName("should_set_status_disabled_and_publish_event_when_active")
    void should_set_status_disabled_and_publish_event_when_active() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ACTIVE.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));
      when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      connectionService.disableConnection(1L, 1L);

      assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.DISABLED.name());

      ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(ConnectionStatusChangedEvent.class);
    }

    @Test
    @DisplayName("should_throw_when_connection_already_archived")
    void should_throw_when_connection_already_archived() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ARCHIVED.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));

      assertThatThrownBy(() -> connectionService.disableConnection(1L, 1L))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("enableConnection")
  class EnableConnection {

    @Test
    @DisplayName("should_set_pending_validation_and_trigger_async_when_disabled")
    void should_set_pending_validation_and_trigger_async_when_disabled() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.DISABLED.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));
      when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      connectionService.enableConnection(1L, 1L);

      assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.PENDING_VALIDATION.name());

      ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(ConnectionStatusChangedEvent.class);
    }

    @Test
    @DisplayName("should_throw_when_not_disabled")
    void should_throw_when_not_disabled() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ACTIVE.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));

      assertThatThrownBy(() -> connectionService.enableConnection(1L, 1L))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("archiveConnection")
  class ArchiveConnection {

    @Test
    @DisplayName("should_archive_when_active")
    void should_archive_when_active() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ACTIVE.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));
      when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      connectionService.archiveConnection(1L, 1L);

      assertThat(conn.getStatus()).isEqualTo(ConnectionStatus.ARCHIVED.name());
    }

    @Test
    @DisplayName("should_throw_when_already_archived")
    void should_throw_when_already_archived() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ARCHIVED.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));

      assertThatThrownBy(() -> connectionService.archiveConnection(1L, 1L))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("triggerSync")
  class TriggerSync {

    @Test
    @DisplayName("should_publish_event_when_connection_active")
    void should_publish_event_when_connection_active() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ACTIVE.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));

      connectionService.triggerSync(1L, 1L, 10L, null);

      ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
      verify(eventPublisher).publishEvent(captor.capture());
      assertThat(captor.getValue()).isInstanceOf(SyncTriggeredEvent.class);
    }

    @Test
    @DisplayName("should_throw_when_connection_not_active")
    void should_throw_when_connection_not_active() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.DISABLED.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));

      assertThatThrownBy(() -> connectionService.triggerSync(1L, 1L, 10L, null))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("updateConnection")
  class UpdateConnection {

    @Test
    @DisplayName("should_update_name_when_valid")
    void should_update_name_when_valid() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ACTIVE.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));
      when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      when(syncStateRepository.findAllByMarketplaceConnectionId(1L))
          .thenReturn(List.of());
      when(connectionMapper.toResponse(any(), any())).thenReturn(null);

      connectionService.updateConnection(1L, new UpdateConnectionRequest("New Name"), 1L);

      assertThat(conn.getName()).isEqualTo("New Name");
    }
  }

  @Nested
  @DisplayName("getConnection")
  class GetConnection {

    @Test
    @DisplayName("should_throw_not_found_when_connection_missing")
    void should_throw_not_found_when_connection_missing() {
      when(connectionRepository.findByIdAndWorkspaceId(99L, 1L))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> connectionService.getConnection(99L, 1L))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("updatePerformanceCredentials")
  class UpdatePerformanceCredentials {

    @Test
    @DisplayName("should_throw_when_marketplace_is_not_ozon")
    void should_throw_when_marketplace_is_not_ozon() {
      var conn = buildConnection(1L, "WB", ConnectionStatus.ACTIVE.name());
      when(connectionRepository.findByIdAndWorkspaceId(1L, 1L))
          .thenReturn(Optional.of(conn));

      assertThatThrownBy(() -> connectionService.updatePerformanceCredentials(
          1L, new io.datapulse.integration.api.UpdatePerformanceCredentialsRequest("id", "secret"),
          1L, 10L))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("connection.marketplace.mismatch");
    }
  }
}
