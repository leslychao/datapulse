package io.datapulse.core.service;

import io.datapulse.core.client.HttpStreamingClient;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class StreamingDownloadService {

  private final HttpStreamingClient httpStreamingClient;

  public Flux<DataBuffer> stream(URI uri, HttpHeaders headers) {
    return httpStreamingClient.getAsDataBufferFlux(uri, headers);
  }

  public Flux<DataBuffer> post(URI uri, HttpHeaders headers, Object body) {
    return httpStreamingClient.postAsDataBufferFlux(uri, headers, body);
  }
}
