package io.datapulse.etl.flow.core;

import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXPECTED_EXECUTION_KEYS;
import static org.assertj.core.api.Assertions.assertThat;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.dto.ExecutionOutcome;
import io.datapulse.etl.dto.IngestStatus;
import io.datapulse.etl.event.EtlSourceRegistry;
import io.datapulse.core.service.account.AccountConnectionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

class EtlOrchestratorFlowConfigTest {

  private final EtlOrchestratorFlowConfig config = new EtlOrchestratorFlowConfig(
      Mockito.mock(RabbitTemplate.class),
      Mockito.mock(AccountConnectionService.class),
      Mockito.mock(EtlSourceRegistry.class),
      Mockito.mock(EtlOrchestrationCommandFactory.class)
  );

  @Test
  void isAggregationCompleteReturnsFalseWhenNotAllExpectedKeysFinished() {
    Message<?> message = messageWithExpectedKeys(
        Set.of("OZON:orders", "WILDBERRIES:orders"),
        outcome("orders", MarketplaceType.OZON, IngestStatus.SUCCESS)
    );

    MessageGroup group = messageGroup(List.of(message));

    boolean complete = ReflectionTestUtils.invokeMethod(config, "isAggregationComplete", group);

    assertThat(complete).isFalse();
  }

  @Test
  void isAggregationCompleteReturnsTrueWhenAllExpectedKeysFinished() {
    Message<?> first = messageWithExpectedKeys(
        Set.of("OZON:orders", "WILDBERRIES:orders"),
        outcome("orders", MarketplaceType.OZON, IngestStatus.SUCCESS)
    );
    Message<?> second = MessageBuilder
        .withPayload(outcome("orders", MarketplaceType.WILDBERRIES, IngestStatus.NO_DATA))
        .setHeader(HDR_ETL_EXPECTED_EXECUTION_KEYS, Set.of("OZON:orders", "WILDBERRIES:orders"))
        .build();

    MessageGroup group = messageGroup(List.of(first, second));

    boolean complete = ReflectionTestUtils.invokeMethod(config, "isAggregationComplete", group);

    assertThat(complete).isTrue();
  }

  private MessageGroup messageGroup(List<Message<?>> messages) {
    MessageGroup group = Mockito.mock(MessageGroup.class);
    Mockito.when(group.getMessages()).thenReturn(messages);
    Mockito.when(group.streamMessages()).thenReturn(messages.stream());
    return group;
  }

  private Message<?> messageWithExpectedKeys(Set<String> expectedKeys, ExecutionOutcome outcome) {
    return MessageBuilder
        .withPayload(outcome)
        .setHeader(HDR_ETL_EXPECTED_EXECUTION_KEYS, expectedKeys)
        .build();
  }

  private ExecutionOutcome outcome(String sourceId, MarketplaceType marketplace, IngestStatus status) {
    return new ExecutionOutcome(
        "req-1",
        10L,
        sourceId,
        marketplace,
        MarketplaceEvent.PRODUCT_DICT,
        LocalDate.of(2025, 1, 1),
        LocalDate.of(2025, 1, 2),
        status,
        100,
        null,
        null
    );
  }
}
