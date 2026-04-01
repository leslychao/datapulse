package io.datapulse.test.config;

import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
public class IntegrationTestInfraConfig {

  @Bean
  public StringRedisTemplate stringRedisTemplate() {
    return Mockito.mock(StringRedisTemplate.class);
  }

  @Bean
  public ConnectionFactory connectionFactory() {
    return Mockito.mock(ConnectionFactory.class);
  }

  @Bean
  public RabbitTemplate rabbitTemplate() {
    return Mockito.mock(RabbitTemplate.class);
  }

  @Bean
  public JavaMailSender javaMailSender() {
    return Mockito.mock(JavaMailSender.class);
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    return Mockito.mock(JwtDecoder.class);
  }
}
