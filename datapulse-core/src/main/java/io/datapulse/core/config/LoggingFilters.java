package io.datapulse.core.config;
    
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
    import reactor.core.publisher.Mono;
    
    public final class LoggingFilters {
      private static final Logger log = LoggerFactory.getLogger(LoggingFilters.class);
      private LoggingFilters() {}
    
      public static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
          log.debug("HTTP {} {}", req.method(), req.url());
          return Mono.just(req);
        });
      }
      public static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(res -> {
          log.debug("HTTP status {}", res.statusCode());
          return Mono.just(res);
        });
      }
    }
