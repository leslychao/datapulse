package io.datapulse.core.service;

import io.datapulse.core.client.HttpStreamingClient;
import io.datapulse.core.client.ReactorResilienceSupport;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingDownloadService {

  private final HttpStreamingClient httpStreamingClient;
  private final MeterRegistry meter;

  public Flux<DataBuffer> stream(
      URI uri,
      HttpHeaders headers,
      Retry retry,
      RateLimiter rateLimiter,
      Bulkhead bulkhead
  ) {
    var bytesCounter = meter.counter("datapulse.download.bytes");
    var successCounter = meter.counter("datapulse.download.success");
    var errorCounter = meter.counter("datapulse.download.errors");

    return ReactorResilienceSupport
        .applyResilience(
            httpStreamingClient.getAsDataBufferFlux(uri, headers),
            rateLimiter,
            bulkhead
        )
        .doOnSubscribe(s -> log.info("Start streaming from {}", uri))
        .doOnNext(buf -> bytesCounter.increment(buf.readableByteCount()))
        .retryWhen(retry)
        .doOnComplete(() -> {
          successCounter.increment();
          log.info("Streaming completed: {}", uri);
        })
        .doOnError(ex -> {
          errorCounter.increment();
          log.error("Streaming error from {}: {}", uri, ex.toString());
        });
  }
}
