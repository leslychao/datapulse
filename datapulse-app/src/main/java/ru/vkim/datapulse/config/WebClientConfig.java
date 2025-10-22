package ru.vkim.datapulse.config;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final MeterRegistry meterRegistry;

    @Bean
    public WebClient webClient() {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .followRedirect(true);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            System.out.println("[HTTP] => " + req.method() + " " + req.url());
            return reactor.core.publisher.Mono.just(req);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            System.out.println("[HTTP] <= " + resp.statusCode());
            return reactor.core.publisher.Mono.just(resp);
        });
    }
}
