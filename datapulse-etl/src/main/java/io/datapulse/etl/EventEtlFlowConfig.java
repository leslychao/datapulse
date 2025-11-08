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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventEtlFlowConfig {

  private static final String HDR_REQ_ID = "X-Request-Id";
  private static final String CH_ETL_ASYNC = "CHANNEL_ETL_ASYNC";

  private final List<EventSource<?>> sources;

  /** DTO, которое принимает HTTP. */
  public record EventJob(
      Long accountId,
      String event,     // например "SALES_FACT"
      LocalDate from,
      LocalDate to,
      String batchId,
      Integer burst,
      FetchParams params // опционально
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

  /** Реактивный канал, который умеет подписываться на Publisher-пейлоады. */
  @Bean(name = CH_ETL_ASYNC)
  public MessageChannel etlAsyncChannel() {
    return new FluxMessageChannel();
  }

  /** HTTP → (ACK + fire-and-forget в именованный канал CH_ETL_ASYNC). */
  @Bean
  public IntegrationFlow httpToEtl(TaskExecutor etlExecutor) {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST))
            .requestPayloadType(EventJob.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED))
        .enrichHeaders(h -> h.headerFunction(HDR_REQ_ID, m -> UUID.randomUUID().toString()))
        .publishSubscribeChannel(ps -> {
          // ===== Async ветка: формируем Publisher и отдаём его в отдельный flow (без reply)
          ps.subscribe(sf -> sf
              .enrichHeaders(h -> h
                  .headerExpression(MessageHeaders.REPLY_CHANNEL, "null")
                  .headerExpression(MessageHeaders.ERROR_CHANNEL, "null"))
              .handle((payload, headers) -> buildMergedFlux((EventJob) payload, headers))
              .channel(CH_ETL_ASYNC)
          );

          // ===== Sync ветка: немедленный ACK JSON (HTTP 202)
          ps.subscribe(sf -> sf.transform(EventJob.class, job -> {
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("status", "accepted");
            ack.put("event", job.event());
            ack.put("batchId", job.batchId() != null ? job.batchId() : UUID.randomUUID().toString());
            if (job.burst() != null) ack.put("burst", job.burst());
            return ack;
          }));
        })
        .get();
  }

  /** Consumer-flow: читает CH_ETL_ASYNC, распаковывает Flux на элементы и обрабатывает их. */
  @Bean
  public IntegrationFlow etlAsyncFlow(TaskExecutor etlExecutor) {
    return IntegrationFlow.from(CH_ETL_ASYNC)
        .split()               // распаковка Flux/Publisher на элементы
        .channel(c -> c.executor(etlExecutor))      // при необходимости распараллелить обработку элементов
        .handle(m -> {
          Object item = m.getPayload();
          // здесь — запись/обработка единичного элемента
          log.debug("ETL item: {}", item);
        })
        .get();
  }

  /** Собирает единый Flux из всех подходящих EventSource без подписки. */
  private Flux<?> buildMergedFlux(EventJob job, Map<String, Object> headers) {
    String reqId = String.valueOf(headers.getOrDefault(HDR_REQ_ID, "n/a"));

    FetchRequest req = new FetchRequest(
        Objects.requireNonNull(job.accountId(), "accountId is required"),
        MarketplaceEvent.valueOf(job.event()),
        Objects.requireNonNull(job.from(), "from is required"),
        Objects.requireNonNull(job.to(), "to is required"),
        job.params()
    );

    var matched = sources.stream()
        .filter(src -> src.event() == req.event())
        .collect(Collectors.toList());

    if (matched.isEmpty()) {
      log.warn("No EventSource for event={} (reqId={})", req.event(), reqId);
      return Flux.empty();
    }

    log.info("ETL start event={} sources={} (reqId={})", req.event(), matched.size(), reqId);

    return Flux.merge(matched.stream().map(src -> src.fetch(req)).toList())
        .doOnSubscribe(s -> log.info("ETL pipeline started event={} reqId={}", req.event(), reqId))
        .doOnError(e -> log.error("ETL pipeline error event={} reqId={}", req.event(), reqId, e))
        .doOnComplete(() -> log.info("ETL pipeline finished event={} reqId={}", req.event(), reqId));
  }

  /** Пример батч-писателя (на будущее), если решишь буферизовать элементы. */
  private Mono<Void> bulkWrite(List<?> batch) {
    return Mono.fromRunnable(() -> log.debug("Batch processed: size={}", batch.size()));
  }
}
