package io.datapulse.etl;

import static io.datapulse.etl.IntegrationChannels.CHANNEL_FETCH_SALES;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

  private static final String HDR_FROM = "from";
  private static final String HDR_TO = "to";
  private static final String HDR_BATCH_ID = "batchId";
  private static final String HDR_SEQ = "seq";

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
    // Асинхронная публикация для быстрого «залпа»
    return new ExecutorChannel(etlExecutor);
  }

  @Bean
  public MessagingTemplate messagingTemplate(
      @Qualifier(CHANNEL_FETCH_SALES) MessageChannel fetchSalesChannel) {
    var tpl = new MessagingTemplate();
    tpl.setDefaultChannel(fetchSalesChannel);
    return tpl;
  }

  /**
   * Параметр burst — сколько сообщений сгенерировать (по умолчанию 300, верхняя граница 10_000).
   */
  public record WbSalesEtlRequest(Long accountId, String from, String to, Integer burst) {}

  @Bean
  public IntegrationFlow wbSalesInboundFlow(MessagingTemplate messagingTemplate) {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST).consumes(MediaType.APPLICATION_JSON_VALUE))
            .requestPayloadType(WbSalesEtlRequest.class)
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
            .replyTimeout(0))
        .handle(WbSalesEtlRequest.class, (req, headers) -> {
          LocalDate from = parseOrDefault(req.from(), LocalDate.now().minusDays(1));
          LocalDate to = parseOrDefault(req.to(), LocalDate.now().minusDays(1));
          int burst = normalizeBurst(req.burst());

          String batchId = UUID.randomUUID().toString();

          // «Залп»: отправляем N сообщений в канал для провокации 429 у внешнего провайдера
          IntStream.range(0, burst)
              .filter(validSeq())
              .forEach(seq ->
                  messagingTemplate.send(
                      MessageBuilder.withPayload(req.accountId())
                          .setHeader(HDR_FROM, from)
                          .setHeader(HDR_TO, to)
                          .setHeader(HDR_BATCH_ID, batchId)
                          .setHeader(HDR_SEQ, seq)
                          .build()
                  )
              );

          return Map.of(
              "status", "accepted",
              "accountId", req.accountId(),
              "from", from.toString(),
              "to", to.toString(),
              "burst", burst,
              "batchId", batchId
          );
        })
        .get();
  }

  private static int normalizeBurst(Integer raw) {
    int value = (raw == null) ? 300 : raw;
    if (value < 1) return 1;
    return Math.min(value, 10_000);
  }

  private static IntPredicate validSeq() {
    return seq -> seq >= 0; // крючок под возможную фильтрацию/шардинг
  }

  private static LocalDate parseOrDefault(String s, LocalDate dflt) {
    try {
      return (s == null || s.isBlank()) ? dflt : LocalDate.parse(s);
    } catch (Exception e) {
      return dflt;
    }
  }
}
