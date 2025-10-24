package io.datapulse.core.client;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class HttpStreamingClient {

  private final WebClient streamingWebClient;

  public Flux<DataBuffer> getAsDataBufferFlux(URI uri, HttpHeaders extraHeaders) {
    return streamingWebClient.get()
        .uri(uri)
        .headers(h -> {
          if (extraHeaders != null) {
            h.addAll(extraHeaders);
          }
          h.setAccept(List.of(MediaType.APPLICATION_JSON));
        })
        .exchangeToFlux(resp -> resp.statusCode().is2xxSuccessful()
            ? resp.bodyToFlux(DataBuffer.class)
            : resp.createException().flatMapMany(Mono::error)
        );
  }
}
