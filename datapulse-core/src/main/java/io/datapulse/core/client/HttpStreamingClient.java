package io.datapulse.core.client;

import java.net.URI;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;

public interface HttpStreamingClient {

  Flux<DataBuffer> getAsDataBufferFlux(URI uri, HttpHeaders headers);

  Flux<DataBuffer> postAsDataBufferFlux(URI uri, HttpHeaders headers, Map<String, ?> body);
}
