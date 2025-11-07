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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventEtlFlowConfig {

  private final List<EventSource<?>> sources;

  // DTO, которое принимает HTTP
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

  @Bean
  public IntegrationFlow httpToEtl(TaskExecutor etlExecutor) {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST))
            .requestPayloadType(EventJob.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED))
        .enrichHeaders(h -> h.headerFunction("X-Request-Id", m -> UUID.randomUUID().toString()))
        .publishSubscribeChannel(ps -> {

          // ===== Async ветка: запускаем реактивный конвейер, не отвечаем в HTTP =====
          ps.subscribe(sf -> sf
              // отрываемся от HTTP-ответа
              .enrichHeaders(h -> h
                  .headerExpression(MessageHeaders.REPLY_CHANNEL, "null")
                  .headerExpression(MessageHeaders.ERROR_CHANNEL,  "null"))
              // уводим работу в пул, чтобы HTTP сразу вернул ACK
              .channel(c -> c.executor(etlExecutor))
              // строим поток и подписываемся (one-way)
              .handle((GenericHandler<EventJob>) (job, headers) -> {
                String reqId = String.valueOf(headers.getOrDefault("X-Request-Id", "n/a"));

                buildMergedFlux(job, headers)
                    .limitRate(256)                 // контролируем потребление (backpressure friendly)
                    .buffer(500)                    // батчи по 500 подряд идущих элементов
                    .concatMap(this::bulkWrite)     // строгий порядок батчей
                    .doOnSubscribe(s -> log.info("ETL pipeline started event={} reqId={}", job.event(), reqId))
                    .doOnError(e -> log.error("ETL pipeline error event={} reqId={}", job.event(), reqId, e))
                    .doOnTerminate(() -> log.info("ETL pipeline finished event={} reqId={}", job.event(), reqId))
                    .subscribe();                   // асинхронно

                return null; // one-way
              }, e -> e.requiresReply(false))
          );

          // ===== Sync ветка: немедленный ACK JSON =====
          ps.subscribe(sf -> sf.transform(EventJob.class, job -> {
            Map<String,Object> ack = new LinkedHashMap<>();
            ack.put("status",  "accepted");
            ack.put("event",   job.event());
            ack.put("batchId", job.batchId() != null ? job.batchId() : UUID.randomUUID().toString());
            if (job.burst() != null) ack.put("burst", job.burst());
            return ack; // тело HTTP-ответа (202)
          }));
        })
        .get();
  }

  /** Собирает единый Flux из всех подходящих EventSource без подписки. */
  private Flux<?> buildMergedFlux(EventJob job, Map<String, Object> headers) {
    var reqId = headers.getOrDefault("X-Request-Id", "n/a");

    // map EventJob -> FetchRequest
    var req = new FetchRequest(
        Objects.requireNonNull(job.accountId(), "accountId is required"),
        MarketplaceEvent.valueOf(job.event()),
        Objects.requireNonNull(job.from(), "from is required"),
        Objects.requireNonNull(job.to(), "to is required"),
        job.params() // может быть null
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
        .doOnError(e -> log.error("[ETL error] event={} (reqId={})", req.event(), reqId, e))
        .doOnComplete(() -> log.info("[ETL done] event={} (reqId={})", req.event(), reqId));
  }

  /**
   * Батч-обработка. Возвращаем Mono, чтобы concatMap строго соблюдал порядок.
   * Тут просто печать — но можно заменить на реактивную запись в БД/шину/внешний API.
   */
  private Mono<Void> bulkWrite(List<?> batch) {
    return Mono.fromRunnable(() -> {
      for (Object item : batch) {
        System.out.println("[ETL item] " + item); // порядок сохранится
      }
      log.debug("Batch processed: size={}", batch.size());
    });
    // Пример для реактивного репозитория:
    // return repository.saveAll(batch).then();
  }
}
