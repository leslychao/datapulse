package io.datapulse.etl;


import static io.datapulse.etl.IntegrationChannels.CHANNEL_RUN_EVENT;

import io.datapulse.etl.EtlInbound.EventJob;
import io.datapulse.etl.route.EventRoute;
import io.datapulse.etl.route.EventRoutesRegistry;
import io.datapulse.marketplaces.event.FetchRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import reactor.core.publisher.Flux;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EventEtlFlow {

  private final EventRoutesRegistry routes;

  @Bean
  public IntegrationFlow runEtlFlow(TaskExecutor etlExecutor) {
    return IntegrationFlow.from(CHANNEL_RUN_EVENT)
        .channel(MessageChannels.executor(etlExecutor))
        .handle((payload, headers) -> {
          EventJob job = (EventJob) payload;

          Optional<EventRoute<?>> routeOpt = routes.route(job.event);
          if (routeOpt.isEmpty()) {
            log.warn("Нет маршрута для события {}", job.event);
            return null;
          }

          EventRoute<?> route = routeOpt.get();

          // теперь создаём единый FetchRequest и передаём его по цепочке
          FetchRequest request = new FetchRequest(
              job.accountId, job.event, job.from, job.to, null
          );

          Flux<?> dtoFlux = route.fetchAll(request);

          dtoFlux
              .doOnSubscribe(
                  s -> log.info("ETL start event={} batch={} seq={}", job.event, job.batchId,
                      job.seq))
              .doOnNext(System.out::println)
              .doOnComplete(
                  () -> log.info("ETL done  event={} batch={} seq={}", job.event, job.batchId,
                      job.seq))
              .subscribe();

          return null;
        })
        .get();
  }
}
