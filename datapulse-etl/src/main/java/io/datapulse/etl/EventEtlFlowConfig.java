package io.datapulse.etl;

import io.datapulse.etl.route.EventSource;
import io.datapulse.marketplaces.event.FetchRequest;
import io.datapulse.marketplaces.event.MarketplaceEvent;
import io.datapulse.marketplaces.event.FetchParams;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageHeaders;
import reactor.core.publisher.Flux;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventEtlFlowConfig {

  private final List<EventSource<?>> sources;

  // Внутренний DTO, который принимает HTTP
  public record EventJob(
      Long accountId,
      String event,     // например "SALES_FACT"
      LocalDate from,
      LocalDate to,
      String batchId,
      Integer burst,
      FetchParams params // на будущее: можно пробрасывать как есть
  ) {}

  @Bean
  public IntegrationFlow httpToEtl() {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST))
            .requestPayloadType(EventJob.class)
            .mappedRequestHeaders("*")
            .statusCodeFunction(m -> HttpStatus.ACCEPTED))
        .enrichHeaders(h -> h.headerFunction("X-Request-Id", m -> UUID.randomUUID().toString()))
        .publishSubscribeChannel(ps -> {

          // === Async: запускаем ETL, ничего не отвечаем в HTTP ===
          ps.subscribe(sf -> sf
              // вычищаем reply/error, чтобы не было попытки ответить в HTTP из асинхронной ветки
              .enrichHeaders(h -> h
                  .headerExpression(MessageHeaders.REPLY_CHANNEL, "null")
                  .headerExpression(MessageHeaders.ERROR_CHANNEL,  "null"))
              // собираем Flux без подписки
              .handle((GenericHandler<EventJob>) this::buildMergedFlux,
                  e -> e.requiresReply(true))
              // распаковываем Flux по одному элементу
              .split()
              // здесь твой sysout/обработка
              .handle((item, headers) -> {
                System.out.println("[ETL item] " + item);
                return null;
              })
          );

          // === Sync: сразу вернуть ACK JSON ===
          ps.subscribe(sf -> sf.transform(EventJob.class, job -> {
            Map<String,Object> ack = new LinkedHashMap<>();
            ack.put("status",  "accepted");
            ack.put("event",   job.event());
            // если batchId не пришёл — сгенерим
            String batchId = (job.batchId() != null) ? job.batchId() : UUID.randomUUID().toString();
            ack.put("batchId", batchId);
            if (job.burst() != null)  ack.put("burst", job.burst());
            return ack;
          }));
        })
        .get();
  }

  /** Собирает единый Flux из всех подходящих EventSource без подписки. */
  private Flux<?> buildMergedFlux(EventJob job, Map<String, Object> headers) {
    var reqId = headers.getOrDefault("X-Request-Id", "n/a");

    // map EventJob -> FetchRequest (params может быть null — ок)
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
}
