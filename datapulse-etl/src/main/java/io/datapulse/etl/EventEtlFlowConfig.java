package io.datapulse.etl;

import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.route.EventSource;
import io.datapulse.marketplaces.event.FetchParams;
import io.datapulse.marketplaces.event.FetchRequest;
import io.datapulse.marketplaces.event.MarketplaceEvent;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventEtlFlowConfig {

  private static final String HDR_REQ_ID = "X-Request-Id";
  private static final String CH_ETL_ERRORS = "CHANNEL_ETL_ERRORS";
  private static final String CH_PROCESS_ETL = "CHANNEL_PROCESS_ETL";

  private final List<EventSource<?>> sources;

  /** DTO, которое принимает HTTP. */
  public record EventJob(
      Long accountId,
      String event,
      LocalDate from,
      LocalDate to,
      String batchId,
      Integer burst,
      FetchParams params
  ) {}

  @Bean
  public TaskExecutor etlExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setThreadNamePrefix("etl-");
    ex.setCorePoolSize(4);
    ex.setMaxPoolSize(16);
    ex.setQueueCapacity(1000);
    ex.initialize();
    return ex;
  }

  /** Канал/топик для ошибок. */
  @Bean(name = CH_ETL_ERRORS)
  public PublishSubscribeChannel etlErrorsChannel() {
    return new PublishSubscribeChannel();
  }

  /** HTTP → (ACK сразу). Копию сообщения уводим в асинхронную ETL-ветку через wireTap. */
  @Bean
  public IntegrationFlow httpToEtl() {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST))
            .requestPayloadType(EventJob.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
            .errorChannel(CH_ETL_ERRORS))
        .enrichHeaders(h -> h.headerFunction(HDR_REQ_ID, m -> UUID.randomUUID().toString()))
        .wireTap(flow -> flow
            .enrichHeaders(h -> h.headerFunction(org.springframework.messaging.MessageHeaders.REPLY_CHANNEL, m -> null))
            .channel(CH_PROCESS_ETL))
        .transform(EventJob.class, job -> {
          Map<String, Object> ack = new LinkedHashMap<>();
          ack.put("status", "accepted");
          ack.put("event", job.event());
          ack.put("batchId", job.batchId() != null ? job.batchId() : UUID.randomUUID().toString());
          if (job.burst() != null) ack.put("burst", job.burst());
          return ack;
        })
        .get();
  }

  @Bean
  public AbstractMessageSplitter etlPublisherSplitter() {
    return new AbstractMessageSplitter() {
      @Override
      protected Object splitMessage(Message<?> message) {
        return buildMergedFlux((EventJob) message.getPayload(), message.getHeaders()); // ← Flux
      }
    };
  }

  /** Асинхронная ETL-ветка: старт из именованного канала. */
  @Bean
  public IntegrationFlow processEtl(TaskExecutor etlExecutor) {
    return IntegrationFlow.from(CH_PROCESS_ETL)
        .enrichHeaders(h -> h
            .headerFunction(MessageHeaders.REPLY_CHANNEL, m -> null)
            .header(MessageHeaders.ERROR_CHANNEL, CH_ETL_ERRORS))
        .split(etlPublisherSplitter())
        .channel(c -> c.executor(etlExecutor))
        .handle(msg -> {
          // построчный лог — на DEBUG, чтобы не шуметь в проде
          if (log.isDebugEnabled()) {
            log.debug("ETL item: {}", msg.getPayload());
          }
        })
        .get();
  }

  /** Error-flow: лог + ACK 202 (чтобы фронт не видел 500). */
  @Bean
  public IntegrationFlow etlErrorFlow() {
    return IntegrationFlow.from(CH_ETL_ERRORS)
        .log(LoggingHandler.Level.ERROR,
            "etlErrorFlow",
            "'ETL ERROR: '+T(org.apache.commons.lang3.exception.ExceptionUtils).getRootCauseMessage(payload)")
        .transform(MessagingException.class, ex -> {
          Map<String, Object> ack = new LinkedHashMap<>();
          ack.put("status", "accepted"); // HTTP-ветка асинхронная
          ack.put("event", "unknown");
          ack.put("batchId", UUID.randomUUID().toString());
          return ack;
        })
        .enrichHeaders(h -> h.header(
            org.springframework.integration.http.HttpHeaders.STATUS_CODE, HttpStatus.ACCEPTED))
        .get();
  }

  /** Собирает единый Flux из всех подходящих EventSource; никаких побочек до subscribe(). */
  private Flux<?> buildMergedFlux(EventJob job, MessageHeaders headers) {
    final String reqId = String.valueOf(headers.getOrDefault(HDR_REQ_ID, "n/a"));

    return Mono.fromCallable(() -> {
          // могут лететь NPE/IAE — поймаем реактивно ниже
          MarketplaceEvent evt = MarketplaceEvent.valueOf(job.event());
          return new FetchRequest(
              Objects.requireNonNull(job.accountId(), "accountId is required"),
              evt,
              Objects.requireNonNull(job.from(), "from is required"),
              Objects.requireNonNull(job.to(),   "to is required"),
              job.params()
          );
        })
        .onErrorMap(ex -> new AppException(
            "INVALID_FETCH_REQUEST",
            "Некорректные параметры ETL-запроса: event=" + job.event() + ", reqId=" + reqId,
            ex
        ))
        .flatMapMany(req -> {
          var matched = sources.stream()
              .filter(src -> src.event() == req.event())
              .toList();

          if (matched.isEmpty()) {
            // Нет источника под событие — считаем это ошибкой конфигурации
            return Flux.error(new AppException(
                "NO_EVENT_SOURCE",
                "Не найден EventSource для события " + req.event() + " (reqId=" + reqId + ")"
            ));
          }

          log.info("ETL started: event={}, sources={}, reqId={}", req.event(), matched.size(), reqId);
          return Flux.merge(matched.stream().map(src -> src.fetch(req)).toList());
        })
        .doOnError(e -> log.error("ETL failed: reqId={}", reqId, e))
        .doOnComplete(() -> log.info("ETL finished: reqId={}", reqId));
  }

  /** Пример батч-писателя (на будущее). */
  private Mono<Void> bulkWrite(List<?> batch) {
    return Mono.fromRunnable(() -> log.debug("Batch processed: size={}", batch.size()));
  }
}
