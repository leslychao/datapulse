package io.datapulse.etl;

import static io.datapulse.etl.IntegrationChannels.CHANNEL_FETCH_SALES;

import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class WbSalesHttpInbound {

  @Bean
  public TaskExecutor etlExecutor() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setThreadNamePrefix("etl-");
    ex.setCorePoolSize(4);
    ex.setMaxPoolSize(16);
    ex.setQueueCapacity(1000);
    ex.initialize();
    return ex;
  }

  @Bean(name = CHANNEL_FETCH_SALES)
  public MessageChannel fetchSalesChannel(TaskExecutor etlExecutor) {
    // ВАЖНО: асинхронный канал вместо DirectChannel
    return new ExecutorChannel(etlExecutor);
  }

  @Bean
  public MessagingTemplate messagingTemplate(
      @Qualifier(CHANNEL_FETCH_SALES) MessageChannel fetchSalesChannel) {
    var tpl = new MessagingTemplate();
    tpl.setDefaultChannel(fetchSalesChannel);
    return tpl;
  }


  public record WbSalesEtlRequest(Long accountId, String from, String to) {

  }

  @Bean
  public IntegrationFlow wbSalesInboundFlow(MessagingTemplate messagingTemplate) {
    return IntegrationFlow
        .from(Http.inboundGateway("/etl/wb/sales")
            .requestMapping(
                m -> m.methods(HttpMethod.POST).consumes(MediaType.APPLICATION_JSON_VALUE))
            .requestPayloadType(WbSalesEtlRequest.class)
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
            .replyTimeout(0))
        .handle(WbSalesEtlRequest.class, (req, headers) -> {
          LocalDate from = parseOrDefault(req.from(), LocalDate.now().minusDays(1));
          LocalDate to = parseOrDefault(req.to(), LocalDate.now().minusDays(1));

          messagingTemplate.send(
              MessageBuilder.withPayload(req.accountId())
                  .setHeader("from", from)
                  .setHeader("to", to)
                  .build()
          );

          return Map.of(
              "status", "accepted",
              "accountId", req.accountId(),
              "from", from.toString(),
              "to", to.toString()
          );
        })
        .get();
  }

  private static LocalDate parseOrDefault(String s, LocalDate dflt) {
    try {
      return (s == null || s.isBlank()) ? dflt : LocalDate.parse(s);
    } catch (Exception e) {
      return dflt;
    }
  }
}
