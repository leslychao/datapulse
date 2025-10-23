package io.datapulse.etl.flow;

import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_ERRORS;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_FETCH_SALES;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_PERSIST_SALES;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_ROUTE_ACCOUNTS;
import static io.datapulse.core.integration.IntegrationConstants.CHANNEL_SALES_CRON;
import static io.datapulse.core.integration.IntegrationConstants.HEADER_ACCOUNT_ID;
import static io.datapulse.core.integration.IntegrationConstants.HEADER_JOB_ID;
import static org.springframework.integration.dsl.Pollers.cron;

import io.datapulse.domain.model.Sale;
import io.datapulse.domain.port.AnalyticsPort;
import io.datapulse.domain.port.MarketplacePort;
import io.datapulse.domain.port.PersistencePort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SalesFlowConfig {

  private final PersistencePort persistencePort;
  private final MarketplacePort wbMarketplaceAdapter;
  private final AnalyticsPort analyticsPort;

  @Bean(name = CHANNEL_SALES_CRON)
  public MessageChannel salesCronChannel() {
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

  @Bean
  public IntegrationFlow salesFlow() {
    return IntegrationFlow
        .fromSupplier(() -> "tick", c -> c.poller(cron("0 0/30 * * * *"))) // каждые 30 минут
        .enrichHeaders(h -> h
            .header(HEADER_JOB_ID, (Function<Object, Object>) m -> UUID.randomUUID().toString()))
        .handle((p, h) -> {
          log.info("Старт ETL продаж, jobId={}", h.get(HEADER_JOB_ID));
          return p;
        })
        .handle((p, h) -> persistencePort.findActiveAccounts())
        .split()
        .channel(CHANNEL_ROUTE_ACCOUNTS)
        .handle((account, headers) -> {
          headers.put(HEADER_ACCOUNT_ID, account);
          return account;
        })
        .handle((acc, h) -> {
          Long accountId = (Long) h.get(HEADER_ACCOUNT_ID);
          LocalDate to = LocalDate.now();
          LocalDate from = to.minusDays(
              1); // инкремент: сутки назад; глубокие синки — отдельным джобом
          List<Sale> sales = wbMarketplaceAdapter.fetchSales(accountId, from, to);
          return sales;
        })
        .split()
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
