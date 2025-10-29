package io.datapulse.etl.route.config;

import io.datapulse.domain.dto.SaleDto;
import io.datapulse.etl.route.EventRoute;
import io.datapulse.etl.route.EventRoutesRegistry;
import io.datapulse.etl.route.EventSource;
import io.datapulse.etl.route.Sources;
import io.datapulse.marketplaces.event.BusinessEvent;
import io.datapulse.marketplaces.event.WbSalesEventFetcher;
import io.datapulse.marketplaces.event.transform.WbSalesTransformer;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SalesFactRouteConfig {

  private final EventRoutesRegistry registry;

  @Bean
  public EventSource<SaleDto> wbSalesSource(
      WbSalesEventFetcher fetcher,
      WbSalesTransformer transformer
  ) {
    return Sources.map(fetcher, transformer);
  }

  @Bean
  public EventRoute<SaleDto> salesFactRoute(List<EventSource<SaleDto>> sources) {
    return new EventRoute<>(sources);
  }

  @PostConstruct
  public void register(EventRoute<SaleDto> route) {
    registry.register(BusinessEvent.SALES_FACT, route);
  }
}
