package io.datapulse.etl.nextgen.config;

import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.EXCHANGE_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.QUEUE_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.QUEUE_EXECUTION_WAIT;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.QUEUE_INGEST;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.QUEUE_MATERIALIZE;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.QUEUE_NORMALIZE;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_EXECUTION;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_EXECUTION_WAIT;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_INGEST;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_MATERIALIZE;
import static io.datapulse.etl.nextgen.constants.NextGenEtlAmqp.ROUTING_NORMALIZE;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NextGenEtlRabbitConfig {

  @Bean
  public DirectExchange nextGenExecutionExchange() {
    return new DirectExchange(EXCHANGE_EXECUTION);
  }

  @Bean
  public DirectExchange nextGenExecutionDlx() {
    return new DirectExchange(EXCHANGE_EXECUTION_DLX);
  }

  @Bean
  public Queue nextGenExecutionQueue() {
    return QueueBuilder.durable(QUEUE_EXECUTION)
        .deadLetterExchange(EXCHANGE_EXECUTION_DLX)
        .deadLetterRoutingKey(ROUTING_EXECUTION_WAIT)
        .build();
  }

  @Bean
  public Queue nextGenExecutionWaitQueue() {
    return QueueBuilder.durable(QUEUE_EXECUTION_WAIT)
        .deadLetterExchange(EXCHANGE_EXECUTION)
        .deadLetterRoutingKey(ROUTING_EXECUTION)
        .build();
  }

  @Bean
  public Queue nextGenIngestQueue() {
    return QueueBuilder.durable(QUEUE_INGEST).build();
  }

  @Bean
  public Queue nextGenNormalizeQueue() {
    return QueueBuilder.durable(QUEUE_NORMALIZE).build();
  }

  @Bean
  public Queue nextGenMaterializeQueue() {
    return QueueBuilder.durable(QUEUE_MATERIALIZE).build();
  }

  @Bean
  public Binding nextGenExecutionBinding() {
    return BindingBuilder.bind(nextGenExecutionQueue())
        .to(nextGenExecutionExchange())
        .with(ROUTING_EXECUTION);
  }

  @Bean
  public Binding nextGenExecutionWaitBinding() {
    return BindingBuilder.bind(nextGenExecutionWaitQueue())
        .to(nextGenExecutionDlx())
        .with(ROUTING_EXECUTION_WAIT);
  }

  @Bean
  public Binding nextGenIngestBinding() {
    return BindingBuilder.bind(nextGenIngestQueue())
        .to(nextGenExecutionExchange())
        .with(ROUTING_INGEST);
  }

  @Bean
  public Binding nextGenNormalizeBinding() {
    return BindingBuilder.bind(nextGenNormalizeQueue())
        .to(nextGenExecutionExchange())
        .with(ROUTING_NORMALIZE);
  }

  @Bean
  public Binding nextGenMaterializeBinding() {
    return BindingBuilder.bind(nextGenMaterializeQueue())
        .to(nextGenExecutionExchange())
        .with(ROUTING_MATERIALIZE);
  }
}
