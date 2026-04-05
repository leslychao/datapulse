package io.datapulse.api.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtlSyncCompletedPayloadResolverTest {

  @Mock private MarketplaceConnectionRepository connectionRepository;

  @InjectMocks private EtlSyncCompletedPayloadResolver resolver;

  @Test
  void resolveWorkspaceId_returnsFromPayload_whenPresent() {
    Optional<Long> ws =
        resolver.resolveWorkspaceId(Map.of("workspaceId", 7L, "connectionId", 1L));

    assertThat(ws).contains(7L);
  }

  @Test
  void resolveWorkspaceId_fallsBackToConnection_whenMissingInPayload() {
    MarketplaceConnectionEntity conn = new MarketplaceConnectionEntity();
    conn.setWorkspaceId(99L);
    when(connectionRepository.findById(5L)).thenReturn(Optional.of(conn));

    Optional<Long> ws =
        resolver.resolveWorkspaceId(Map.of("connectionId", 5L, "jobExecutionId", 10L));

    assertThat(ws).contains(99L);
  }

  @Test
  void resolveForPostSyncTriggers_parsesStringIds() {
    Optional<EtlSyncCompletedPayload> payload =
        resolver.resolveForPostSyncTriggers(
            Map.of("workspaceId", "12", "connectionId", "34", "jobExecutionId", "56"));

    assertThat(payload)
        .contains(new EtlSyncCompletedPayload(12L, 34L, 56L));
  }
}
