package io.datapulse.core.service;

import io.datapulse.core.client.HttpStreamingClient;
import io.datapulse.core.client.ReactorResilienceSupport;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
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

  public Flux<DataBuffer> stream(
      URI uri,
      HttpHeaders headers,
      Retry retry,
      RateLimiter rateLimiter,
      Bulkhead bulkhead
  ) {
    return pipeline(
        ReactorResilienceSupport.applyResilience(
            httpStreamingClient.getAsDataBufferFlux(uri, headers),
            rateLimiter,
            bulkhead
        ),
        uri,
        retry
    );
  }

  public Flux<DataBuffer> post(
      URI uri,
      HttpHeaders headers,
      Object body,
      Retry retry,
      RateLimiter rateLimiter,
      Bulkhead bulkhead
  ) {
    return pipeline(
        ReactorResilienceSupport.applyResilience(
            httpStreamingClient.postAsDataBufferFlux(uri, headers, body),
            rateLimiter,
            bulkhead
        ),
        uri,
        retry
    );
  }

  private Flux<DataBuffer> pipeline(Flux<DataBuffer> source, URI uri, Retry retry) {
    return source
        .doOnSubscribe(s -> log.info("Start streaming from {}", uri))
        .retryWhen(retry)
        .doOnComplete(() -> log.info("Streaming completed: {}", uri))
        .doOnError(ex -> log.error("Streaming error from {}: {}", uri, ex.toString()));
  }
}
