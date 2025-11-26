package io.datapulse.etl.config;


import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EtlExecutionListenerConfig {

  @Bean
  public SimpleRabbitListenerContainerFactory etlExecutionListenerContainerFactory(
      ConnectionFactory connectionFactory
  ) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setConcurrentConsumers(1);
    factory.setMaxConcurrentConsumers(4);
    factory.setPrefetchCount(1);
    factory.setDefaultRequeueRejected(false);
    return factory;
  }
}
