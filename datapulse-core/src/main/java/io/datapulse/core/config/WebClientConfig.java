package io.datapulse.core.config;
    
    import io.netty.channel.ChannelOption;
    import io.netty.handler.timeout.ReadTimeoutHandler;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.http.client.reactive.ReactorClientHttpConnector;
    import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
    import org.springframework.web.reactive.function.client.WebClient;
    import reactor.netty.http.client.HttpClient;
    
    @Configuration
    public class WebClientConfig {
    
      @Bean
      public WebClient webClient(
          @Value("${http.client.connect-timeout-ms:3000}") int connectTimeoutMs,
          @Value("${http.client.read-timeout-ms:5000}") int readTimeoutMs) {
    
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMs / 1000)));
    
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(ExchangeFilterFunction.ofRequestProcessor(MdcFilters::enrichRequestWithMdc))
            .filter(LoggingFilters.logRequest())
            .filter(LoggingFilters.logResponse())
            .build();
      }
    
      // Примечание: ETag-фильтр не включаем по умолчанию (использовать точечно для справочников).
    }
