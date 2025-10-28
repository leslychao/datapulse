package io.datapulse.core.service;

import io.datapulse.core.client.HttpStreamingClient;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Сервис тупо стримит байты (GET/POST) и логирует.
 * Без ретраев/лимитеров/булкхедов — оркестрация выше (в адаптере).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingDownloadService {

  private final HttpStreamingClient httpStreamingClient;

  public Flux<DataBuffer> stream(URI uri, HttpHeaders headers) {
    return httpStreamingClient
        .getAsDataBufferFlux(uri, headers)
        .doOnSubscribe(s -> log.info("Start streaming from {}", uri))
        .doOnComplete(() -> log.info("Streaming completed: {}", uri))
        .doOnError(ex -> log.error("Streaming error from {}", uri, ex));
  }

  public Flux<DataBuffer> post(URI uri, HttpHeaders headers, Object body) {
    return httpStreamingClient
        .postAsDataBufferFlux(uri, headers, body)
        .doOnSubscribe(s -> log.info("Start POST streaming to {}", uri))
        .doOnComplete(() -> log.info("POST streaming completed: {}", uri))
        .doOnError(ex -> log.error("POST streaming error to {}", uri, ex));
  }
}
