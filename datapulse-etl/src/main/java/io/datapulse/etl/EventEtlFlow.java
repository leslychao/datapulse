package io.datapulse.etl;

import static io.datapulse.etl.IntegrationChannels.CHANNEL_RUN_EVENT;

import io.datapulse.etl.route.EventSource;
import io.datapulse.marketplaces.event.FetchRequest;
import java.util.List;
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

  private final List<EventSource<?>> sources;

  @Bean
  public IntegrationFlow runEtlFlow(TaskExecutor etlExecutor) {
    return IntegrationFlow.from(CHANNEL_RUN_EVENT).channel(MessageChannels.executor(etlExecutor))
        .handle((payload, headers) -> {
          var fetchRequest = (FetchRequest) payload;

          var matched = sources.stream().filter(src -> src.event() == fetchRequest.event())
              .toList();

          if (matched.isEmpty()) {
            log.warn("No sources found for event={}", fetchRequest.event());
            return null;
          }

          Flux<?> flux = Flux.merge(
              matched.stream().map(source -> source.fetch(fetchRequest)).toList());

          flux.doOnSubscribe(s -> log.info("ETL start event={}", fetchRequest.event()))
              .doOnNext(item -> log.info("ETL item: {}", item))
              .doOnError(e -> log.error("ETL error event={}", fetchRequest.event(), e))
              .doOnComplete(() -> log.info("ETL done  event={}", fetchRequest.event())).subscribe();

          return null;
        }).get();
  }
}
