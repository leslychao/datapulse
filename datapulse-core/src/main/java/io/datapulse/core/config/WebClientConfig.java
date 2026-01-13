package io.datapulse.core.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebClientConfig {

  @Bean
  public HttpClient httpClient(
      @Value("${webclient.connect-timeout-millis:10000}") int connectTimeoutMillis,
      @Value("${webclient.response-timeout-seconds:30}") int responseTimeoutSeconds,
      @Value("${webclient.read-timeout-seconds:30}") int readTimeoutSeconds,
      @Value("${webclient.write-timeout-seconds:30}") int writeTimeoutSeconds
  ) {
    return HttpClient.create()
        .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds))
            .addHandlerLast(new WriteTimeoutHandler(writeTimeoutSeconds)));
  }

  @Bean
  public WebClient streamingWebClient(
      @Value("${webclient.max-in-memory-size:262144}") int maxInMemorySize,
      HttpClient httpClient,
      ExchangeFilterFunction statusLoggingFilter
  ) {
    ExchangeStrategies streamingStrategies = ExchangeStrategies.builder()
        .codecs(c -> c.defaultCodecs().maxInMemorySize(maxInMemorySize))
        .build();

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .exchangeStrategies(streamingStrategies)
        .filters(fns -> fns.add(0, statusLoggingFilter))
        .build();
  }

  @Bean
  public ExchangeFilterFunction statusLoggingFilter() {
    return (request, next) -> {
      long start = System.nanoTime();
      String method = request.method().name();
      String url = request.url().toString();

      return next.exchange(request)
          .doOnNext(resp -> {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            log.info("HTTP {} {} -> {} ({} ms)", method, url, resp.statusCode().value(), tookMs);
          })
          .doOnError(err -> {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("HTTP {} {} -> ERROR: {} ({} ms)", method, url, err.toString(), tookMs);
          });
    };
  }
}
