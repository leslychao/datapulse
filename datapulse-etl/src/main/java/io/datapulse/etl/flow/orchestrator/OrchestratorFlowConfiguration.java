package io.datapulse.etl.flow.orchestrator;

import static io.datapulse.etl.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.flow.core.FlowChannels.CH_ORCHESTRATE;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_ACCOUNT_ID;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_DATE_FROM;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_DATE_TO;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_EVENT;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_EVENT_AGGREGATION;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_EXPECTED_SOURCE_IDS;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_REQUEST_ID;
import static io.datapulse.etl.flow.core.FlowHeaders.HDR_SOURCE_ID;

import io.datapulse.domain.SyncStatus;
import io.datapulse.etl.dto.EtlRunRequest;
import io.datapulse.etl.dto.OrchestrationCommand;
import io.datapulse.etl.flow.core.FlowChannels;
import io.datapulse.etl.flow.core.model.EventAggregation;
import io.datapulse.etl.flow.core.model.ExecutionDescriptor;
import io.datapulse.etl.flow.core.model.ExecutionPlan;
import io.datapulse.etl.flow.core.registry.ExecutionRegistry;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class OrchestratorFlowConfiguration {

  private final RabbitTemplate etlExecutionRabbitTemplate;
  private final OrchestrationCommandMapper commandMapper;
  private final ExecutionPlanFactory executionPlanFactory;
  private final ExecutionRegistry executionRegistry;

  public OrchestratorFlowConfiguration(
      RabbitTemplate etlExecutionRabbitTemplate,
      OrchestrationCommandMapper commandMapper,
      ExecutionPlanFactory executionPlanFactory,
      ExecutionRegistry executionRegistry
  ) {
    this.etlExecutionRabbitTemplate = etlExecutionRabbitTemplate;
    this.commandMapper = commandMapper;
    this.executionPlanFactory = executionPlanFactory;
    this.executionRegistry = executionRegistry;
  }

  @Bean(name = CH_ORCHESTRATE)
  public MessageChannel orchestratorChannel() {
    return new DirectChannel();
  }

  @Bean
  public TaskExecutor orchestratorExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("etl-orchestrator-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean
  public IntegrationFlow orchestrationHttpInboundFlow() {
    return IntegrationFlow.from(
            Http.inboundGateway("/api/etl/run")
                .requestPayloadType(EtlRunRequest.class)
                .statusCodeFunction(message -> HttpStatus.ACCEPTED)
        )
        .transform(EtlRunRequest.class, commandMapper::fromRequest)
        .channel(CH_ORCHESTRATE)
        .transform(OrchestrationCommand.class, this::acceptedResponse)
        .get();
  }

  @Bean
  public IntegrationFlow orchestratorFlow(TaskExecutor orchestratorExecutor) {
    return IntegrationFlow.from(CH_ORCHESTRATE)
        .enrichHeaders(headers -> headers
            .headerChannelsToString()
            .headerFunction(HDR_REQUEST_ID, msg -> ((OrchestrationCommand) msg.getPayload()).requestId())
            .headerFunction(HDR_ACCOUNT_ID, msg -> ((OrchestrationCommand) msg.getPayload()).accountId())
            .headerFunction(HDR_EVENT, msg -> ((OrchestrationCommand) msg.getPayload()).event().name())
            .headerFunction(HDR_DATE_FROM, msg -> ((OrchestrationCommand) msg.getPayload()).from())
            .headerFunction(HDR_DATE_TO, msg -> ((OrchestrationCommand) msg.getPayload()).to())
        )
        .handle(OrchestrationCommand.class, (command, headers) -> executionPlanFactory.buildPlan(command))
        .enrichHeaders(h -> h.headerFunction(
            HDR_EXPECTED_SOURCE_IDS,
            msg -> ((ExecutionPlan) msg.getPayload()).sourceIds().toArray(String[]::new)
        ))
        .enrichHeaders(headers -> headers.headerFunction(
            HDR_EVENT_AGGREGATION,
            message -> executionRegistry.registerPlan(
                (ExecutionPlan) message.getPayload(),
                ((ExecutionPlan) message.getPayload()).executions().getFirst().event().name()
            )
        ))
        .wireTap(tap -> tap
            .transform(message -> message.getHeaders().get(HDR_EVENT_AGGREGATION, EventAggregation.class))
            .filter(EventAggregation.class::isInstance)
            .channel(FlowChannels.CH_EVENT_AUDIT)
        )
        .split(ExecutionPlan.class, ExecutionPlan::executions)
        .channel(c -> c.executor(orchestratorExecutor))
        .enrichHeaders(headers -> headers
            .headerFunction(HDR_SOURCE_ID, msg -> ((ExecutionDescriptor) msg.getPayload()).sourceId())
            .headerFunction(HDR_RAW_TABLE, msg -> ((ExecutionDescriptor) msg.getPayload()).rawTable())
        )
        .handle(Amqp.outboundAdapter(etlExecutionRabbitTemplate)
            .exchangeName(EXCHANGE_EXECUTION)
            .routingKey(ROUTING_KEY_EXECUTION),
            endpoint -> endpoint.requiresReply(false))
        .log(LoggingHandler.Level.INFO, m -> "Execution dispatched for request=" + m.getHeaders().get(HDR_REQUEST_ID))
        .get();
  }

  private Map<String, Object> acceptedResponse(OrchestrationCommand command) {
    return Map.of(
        "status", SyncStatus.PENDING.name().toLowerCase(),
        "requestId", command.requestId(),
        "event", command.event().name(),
        "from", command.from(),
        "to", command.to()
    );
  }
}
