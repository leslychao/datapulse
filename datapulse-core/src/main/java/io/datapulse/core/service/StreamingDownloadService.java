package io.datapulse.core.service;

import io.datapulse.core.client.BlockingOps;
import io.datapulse.core.client.HttpStreamingClient;
import io.datapulse.core.client.ReactorResilienceSupport;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingDownloadService {

  private final HttpStreamingClient httpStreamingClient;
  private final MeterRegistry meter;

  public Mono<Path> downloadToFile(
      URI uri,
      HttpHeaders headers,
      Path targetFile,
      Retry retry,
      RateLimiter rateLimiter,
      Bulkhead bulkhead
  ) {
    final Path tmp = targetFile.resolveSibling(targetFile.getFileName() + ".part");

    var bytesCounter = meter.counter("datapulse.download.bytes");
    var successCounter = meter.counter("datapulse.download.success");
    var errorCounter = meter.counter("datapulse.download.errors");

    return ReactorResilienceSupport
        .applyResilience(
            httpStreamingClient.getAsDataBufferFlux(uri, headers),
            rateLimiter,
            bulkhead)
        .doOnSubscribe(s -> log.info("Start download {} → {}", uri, targetFile))
        .doOnNext((DataBuffer buf) -> bytesCounter.increment(buf.readableByteCount()))
        .as(data -> DataBufferUtils.write(
            data,
            tmp,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE))
        .retryWhen(retry)
        .then(BlockingOps.supplyBlocking(() ->
            Files.move(tmp, targetFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        ))
        .thenReturn(targetFile)
        .doOnSuccess(p -> {
          successCounter.increment();
          log.info("File downloaded successfully: {}", p);
        })
        .onErrorResume(ex ->
            BlockingOps.runBlocking(() -> {
                  errorCounter.increment();
                  log.error("Error during file download from {}: {}", uri, ex.toString());
                  try {
                    Files.deleteIfExists(tmp);
                  } catch (Exception ignore) {
                  }
                })
                .then(Mono.error(ex))
        );
  }
}
