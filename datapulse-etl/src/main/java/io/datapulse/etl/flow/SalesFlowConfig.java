package io.datapulse.etl.flow;

import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_ERRORS;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_FETCH_SALES;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_PERSIST_SALES;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_ROUTE_ACCOUNTS;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_SALES_CRON;
import static io.datapulse.core.integration.IntegrationConstants.HEADER_JOB_ID;

import io.datapulse.domain.model.Account; // FIX: используем доменную модель
import io.datapulse.domain.model.Sale;
import io.datapulse.domain.port.AnalyticsPort;
import io.datapulse.domain.port.MarketplacePort;
import io.datapulse.domain.port.PersistencePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.http.dsl.Http;
import org.springframework.messaging.MessageChannel;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SalesFlowConfig {

  private final PersistencePort persistencePort;
  private final MarketplacePort wbMarketplaceAdapter;
  private final AnalyticsPort analyticsPort;

  @Value("${etl.sales.cron:0 0/30 * * * *}")
  private String cron;

  @Bean(name = CHANNEL_SALES_CRON)
  public MessageChannel salesTriggerChannel() {
    return MessageChannels.direct().getObject();
  }

  @Bean(name = CHANNEL_ROUTE_ACCOUNTS)
  public MessageChannel routeAccountsChannel() {
    return MessageChannels.direct().getObject();
  }

  @Bean(name = CHANNEL_FETCH_SALES)
  public MessageChannel fetchSalesChannel() {
    return MessageChannels.direct().getObject();
  }

  @Bean(name = CHANNEL_PERSIST_SALES)
  public MessageChannel persistSalesChannel() {
    return MessageChannels.direct().getObject();
  }

  @Bean(name = CHANNEL_ERRORS)
  public MessageChannel errorsChannel() {
    return MessageChannels.publishSubscribe().getObject();
  }

  // Крон → кладём сообщения в CHANNEL_SALES_CRON
  @Bean
  public IntegrationFlow salesCronSource() {
    return IntegrationFlow
        .fromSupplier(() -> "cron", c -> c.poller(p -> p.cron(cron)))
        // FIX: генерируем UUID корректно, а не кладём лямбду как значение
        .enrichHeaders(h -> h.headerFunction(HEADER_JOB_ID, m -> UUID.randomUUID().toString()))
        .channel(CHANNEL_SALES_CRON)
        .get();
  }

  // HTTP Inbound (POST /api/etl/run/sales) + wireTap в основной ETL
  @Bean
  public IntegrationFlow salesHttpInbound() {
    return IntegrationFlow
        .from(Http.inboundGateway("/api/etl/run/sales")
            .requestMapping(m -> m.methods(HttpMethod.POST))
            .requestPayloadType(String.class)
            .statusCodeFunction(m -> HttpStatus.OK))
        .enrichHeaders(h -> h.headerFunction(HEADER_JOB_ID, m -> UUID.randomUUID().toString()))
        .transform(p -> (p == null || p.toString().isBlank()) ? "manual" : p)
        .wireTap(CHANNEL_SALES_CRON)
        // ⬇⬇⬇ вот эта строка была причиной ошибки — меняем transform(...) на handle(...)
        .handle((payload, headers) -> {
          String jobId = String.valueOf(headers.get(HEADER_JOB_ID));
          return Map.of("status", "accepted", "jobId", jobId, "trigger", payload);
        })
        .get();
  }

  // Основной ETL flow
  @Bean
  public IntegrationFlow salesFlow() {
    return IntegrationFlow.from(CHANNEL_SALES_CRON)
        .handle((p, h) -> {
          log.info("Старт ETL продаж, jobId={}, trigger={}", h.get(HEADER_JOB_ID), p);
          return p;
        })
        .handle((p, h) -> persistencePort.findActiveAccounts()) // List<Account>
        .split() // Account
        .channel(CHANNEL_ROUTE_ACCOUNTS)
        // FIX: никаких кастов к Long — работаем с Account и берём id
        .handle(Account.class, (account, h) -> {
          LocalDate to = LocalDate.now();
          LocalDate from = to.minusDays(1);
          List<Sale> sales = wbMarketplaceAdapter.fetchSales(account.getId(), from, to);
          return sales; // List<Sale>
        })
        .split() // Sale
        .channel(CHANNEL_FETCH_SALES)
        .aggregate(a -> a.releaseStrategy(g -> g.size() >= 500).groupTimeout(2000))
        .handle((payload, h) -> {
          @SuppressWarnings("unchecked")
          List<Sale> batch = (List<Sale>) payload;
          persistencePort.upsertSalesBatch(batch);
          return null;
        })
        .channel(CHANNEL_PERSIST_SALES)
        .handle((p, h) -> {
          analyticsPort.refreshSalesDailyMaterializedView();
          return null;
        })
        .get();
  }

  @Bean
  public IntegrationFlow errorFlow() {
    return IntegrationFlow.from(CHANNEL_ERRORS)
        .handle((p, h) -> {
          log.error("Ошибка в ETL продаж: {}", p);
          return null;
        })
        .get();
  }
}
