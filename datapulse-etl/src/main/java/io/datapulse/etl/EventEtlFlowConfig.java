package io.datapulse.etl;

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
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
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
  private static final String CH_ETL_ASYNC = "CHANNEL_ETL_ASYNC";
  private static final String CH_ETL_ERRORS = "CHANNEL_ETL_ERRORS";

  private final List<EventSource<?>> sources;

  /**
   * DTO, которое принимает HTTP.
   */
  public record EventJob(
      Long accountId,
      String event,
      LocalDate from,
      LocalDate to,
      String batchId,
      Integer burst,
      FetchParams params
  ) {

  }

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

  /**
   * Реактивный канал для Publisher-пейлоадов.
   */
  @Bean(name = CH_ETL_ASYNC)
  public MessageChannel etlAsyncChannel() {
    return new FluxMessageChannel();
  }

  /**
   * Канал/топик для ошибок (единый бин, на него и ссылаемся).
   */
  @Bean(name = CH_ETL_ERRORS)
  public MessageChannel etlErrorsChannel() {
    return new PublishSubscribeChannel();
  }

  /**
   * HTTP → (ACK сразу) + асинхронный запуск ETL; ошибки HTTP-ветки → CH_ETL_ERRORS.
   */
  @Bean
  public IntegrationFlow httpToEtl(TaskExecutor etlExecutor) {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST))
            .requestPayloadType(EventJob.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED)
            .errorChannel(CH_ETL_ERRORS)) // <-- строкой, чтобы использовать БИН канала
        .enrichHeaders(h -> h.headerFunction(HDR_REQ_ID, m -> UUID.randomUUID().toString()))
        .publishSubscribeChannel(ps -> {
          // async-ветка: готовим ленивый Flux и отправляем в реактивный канал
          ps.subscribe(sf -> sf
              .enrichHeaders(h -> h
                  .header(MessageHeaders.ERROR_CHANNEL, etlErrorsChannel()) // бин в заголовок
                  .headerExpression(MessageHeaders.REPLY_CHANNEL, "null"))
              .handle((payload, headers) -> buildMergedFlux((EventJob) payload, headers))
              .channel(CH_ETL_ASYNC));

          // sync-ветка: немедленный ACK (HTTP 202)
          ps.subscribe(sf -> sf.transform(EventJob.class, job -> {
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("status", "accepted");
            ack.put("event", job.event());
            ack.put("batchId",
                job.batchId() != null ? job.batchId() : UUID.randomUUID().toString());
            if (job.burst() != null) {
              ack.put("burst", job.burst());
            }
            return ack;
          }));
        })
        .get();
  }

  /**
   * Consumer: распаковываем Publisher → элементы и обрабатываем.
   */
  @Bean
  public IntegrationFlow etlAsyncFlow(TaskExecutor etlExecutor) {
    return IntegrationFlow.from(CH_ETL_ASYNC)
        .split()                 // важно: иначе придёт FluxPeek
        .channel(c -> c.executor(etlExecutor))
        .handle(m -> {
          Object item = m.getPayload();
          log.info("ETL item: {}", item);
          // MessageHandler: ничего не возвращаем
        })
        .get();
  }

  /**
   * Error-flow: лог + формирование ACK (202), чтобы фронт не видел 500.
   */
  @Bean
  public IntegrationFlow etlErrorFlow() {
    return IntegrationFlow.from(CH_ETL_ERRORS)
        .log(org.springframework.integration.handler.LoggingHandler.Level.ERROR,
            "etlErrorFlow",
            "'ETL ERROR: '+T(org.apache.commons.lang3.exception.ExceptionUtils).getRootCauseMessage(payload)")
        // формируем ответ, который отдаст inbound-gateway вместо 500
        .transform(MessagingException.class, ex -> {
          Map<String, Object> ack = new LinkedHashMap<>();
          ack.put("status", "accepted"); // мы уже ушли в async
          ack.put("event", "unknown");
          ack.put("batchId", UUID.randomUUID().toString());
          return ack;
        })
        .enrichHeaders(h -> h.header(
            org.springframework.integration.http.HttpHeaders.STATUS_CODE, HttpStatus.ACCEPTED))
        .get();
  }

  /**
   * Собирает единый Flux из всех подходящих EventSource; никаких побочек до subscribe().
   */
  private Flux<?> buildMergedFlux(EventJob job, Map<String, Object> headers) {
    String reqId = String.valueOf(headers.getOrDefault(HDR_REQ_ID, "n/a"));

    // валидация входа; если упадёт — поймает inbound.errorChannel и вернёт 202-ACK из etlErrorFlow
    FetchRequest req = new FetchRequest(
        Objects.requireNonNull(job.accountId(), "accountId is required"),
        MarketplaceEvent.valueOf(job.event()),
        Objects.requireNonNull(job.from(), "from is required"),
        Objects.requireNonNull(job.to(), "to is required"),
        job.params()
    );

    var matched = sources.stream()
        .filter(src -> src.event() == req.event())
        .toList();

    if (matched.isEmpty()) {
      log.warn("No EventSource for event={} (reqId={})", req.event(), reqId);
      return Flux.empty()
          .doOnSubscribe(
              s -> log.info("ETL pipeline started event={} reqId={}", req.event(), reqId))
          .doOnComplete(
              () -> log.info("ETL pipeline finished event={} reqId={}", req.event(), reqId));
    }

    log.info("ETL start event={} sources={} (reqId={})", req.event(), matched.size(), reqId);

    // ленивое создание — только при подписке consumer’ом
    return Flux.defer(() ->
            Flux.merge(matched.stream().map(src -> src.fetch(req)).toList())
        )
        .doOnSubscribe(s -> log.info("ETL pipeline started event={} reqId={}", req.event(), reqId))
        .doOnError(e -> log.error("ETL pipeline error event={} reqId={}", req.event(), reqId, e))
        .doOnComplete(
            () -> log.info("ETL pipeline finished event={} reqId={}", req.event(), reqId));
  }

  /**
   * Пример батч-писателя (на будущее).
   */
  private Mono<Void> bulkWrite(List<?> batch) {
    return Mono.fromRunnable(() -> log.debug("Batch processed: size={}", batch.size()));
  }
}
