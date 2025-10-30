package io.datapulse.etl;

import static io.datapulse.etl.IntegrationChannels.CHANNEL_RUN_EVENT;

import io.datapulse.marketplaces.event.BusinessEvent;
import io.datapulse.marketplaces.event.FetchParams;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class EtlInbound {

  public record EtlRunRequest(Long accountId, String event, String from, String to, Integer burst) {

  }

  @Builder
  @Getter
  public static final class EventJob {

    public final long accountId;
    public final BusinessEvent event;
    public final LocalDate from;
    public final LocalDate to;
    public final String batchId;
    public final int burst;
    @With
    public final int seq;
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

  @Bean(name = CHANNEL_RUN_EVENT)
  public MessageChannel runEventChannel(TaskExecutor etlExecutor) {
    return new ExecutorChannel(etlExecutor);
  }

  @Bean
  public IntegrationFlow etlInboundFlow() {
    return IntegrationFlow.from(Http.inboundGateway("/api/etl/run")
            .requestMapping(m -> m.methods(HttpMethod.POST).consumes(MediaType.APPLICATION_JSON_VALUE))
            .requestPayloadType(EtlRunRequest.class).statusCodeFunction(m -> HttpStatus.ACCEPTED))
        .transform(EtlRunRequest.class, req -> EventJob.builder().accountId(req.accountId())
            .event(BusinessEvent.valueOf(req.event()))
            .from(parseOrDefault(req.from(), LocalDate.now().minusDays(120)))
            .to(parseOrDefault(req.to(), LocalDate.now().minusDays(1)))
            .batchId(UUID.randomUUID().toString()).burst(normalizeBurst(req.burst())).seq(-1)
            .build())
        .wireTap(flow -> flow.split(EventJob.class, job -> replicate(job, job.burst))
            .transform(EventJob.class,
                job -> new io.datapulse.marketplaces.event.FetchRequest(job.getAccountId(),
                    job.getEvent(), job.getFrom(), job.getTo(), FetchParams.empty()))
            .channel(CHANNEL_RUN_EVENT))
        // Немедленный ответ клиенту (202)
        .transform(EventJob.class,
            job -> Map.of("status", "accepted", "event", job.getEvent().name(), "batchId",
                job.getBatchId(), "burst", job.getBurst())).get();
  }

  private static List<EventJob> replicate(EventJob job, int times) {
    return IntStream.range(0, times).mapToObj(job::withSeq).toList();
  }

  private static int normalizeBurst(Integer raw) {
    int v = (raw == null) ? 1 : raw;
    return Math.max(1, Math.min(v, 10_000));
  }

  private static LocalDate parseOrDefault(String s, LocalDate dflt) {
    try {
      return (s == null || s.isBlank()) ? dflt : LocalDate.parse(s);
    } catch (Exception e) {
      return dflt;
    }
  }
}
