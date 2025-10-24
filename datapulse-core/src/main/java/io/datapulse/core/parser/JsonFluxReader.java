package io.datapulse.core.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import reactor.core.publisher.Flux;

public class JsonFluxReader {

  private final Jackson2JsonDecoder decoder;

  public JsonFluxReader(ObjectMapper mapper) {
    this.decoder = new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON);
  }

  public <T> Flux<T> readArray(Flux<DataBuffer> buffers, Class<T> type) {
    return decoder.decode(
        buffers,
        ResolvableType.forClass(type),
        MediaType.APPLICATION_JSON,
        Map.of()
    ).cast(type);
  }
}
