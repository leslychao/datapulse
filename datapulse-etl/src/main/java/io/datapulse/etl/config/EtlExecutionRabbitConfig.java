package io.datapulse.etl.config;

import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_EXECUTION_DLX;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.EXCHANGE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_EXECUTION;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_EXECUTION_WAIT;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.QUEUE_TASKS;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_EXECUTION_WAIT;
import static io.datapulse.etl.v1.flow.core.EtlExecutionAmqpConstants.ROUTING_KEY_TASKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EtlExecutionRabbitConfig {

  @Bean
  public DirectExchange etlTasksExchange() {
    return new DirectExchange(EXCHANGE_TASKS, true, false);
  }

  @Bean
  public DirectExchange etlExecutionExchange() {
    return new DirectExchange(EXCHANGE_EXECUTION, true, false);
  }

  @Bean
  public DirectExchange etlExecutionDlxExchange() {
    return new DirectExchange(EXCHANGE_EXECUTION_DLX, true, false);
  }

  @Bean
  public Queue etlTasksQueue() {
    return new Queue(QUEUE_TASKS, true);
  }

  @Bean
  public Queue etlExecutionQueue() {
    return new Queue(QUEUE_EXECUTION, true);
  }

  @Bean
  public Queue etlExecutionWaitQueue() {
    return new Queue(
        QUEUE_EXECUTION_WAIT,
        true,
        false,
        false,
        Map.of(
            "x-dead-letter-exchange", EXCHANGE_EXECUTION,
            "x-dead-letter-routing-key", ROUTING_KEY_EXECUTION
        )
    );
  }

  @Bean
  public Binding etlTasksBinding(Queue etlTasksQueue, DirectExchange etlTasksExchange) {
    return BindingBuilder.bind(etlTasksQueue).to(etlTasksExchange).with(ROUTING_KEY_TASKS);
  }

  @Bean
  public Binding etlExecutionBinding(Queue etlExecutionQueue, DirectExchange etlExecutionExchange) {
    return BindingBuilder.bind(etlExecutionQueue).to(etlExecutionExchange)
        .with(ROUTING_KEY_EXECUTION);
  }

  @Bean
  public Binding etlExecutionWaitBinding(Queue etlExecutionWaitQueue,
      DirectExchange etlExecutionDlxExchange) {
    return BindingBuilder.bind(etlExecutionWaitQueue).to(etlExecutionDlxExchange)
        .with(ROUTING_KEY_EXECUTION_WAIT);
  }

  @Bean
  public MessageConverter etlExecutionMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  public RabbitTemplate etlExecutionRabbitTemplate(
      ConnectionFactory connectionFactory,
      MessageConverter etlExecutionMessageConverter
  ) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(etlExecutionMessageConverter);
    return template;
  }
}
