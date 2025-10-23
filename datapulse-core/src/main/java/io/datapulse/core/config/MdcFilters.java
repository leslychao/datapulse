package io.datapulse.core.config;
    
    import org.springframework.web.reactive.function.client.ClientRequest;
    import reactor.core.publisher.Mono;
    
    final class MdcFilters {
      private MdcFilters() {}
      static Mono<ClientRequest> enrichRequestWithMdc(ClientRequest request) {
        // Небольшая заглушка: при необходимости добавить заголовки trace/job/tenant.
        return Mono.just(request);
      }
    }
