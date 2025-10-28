package io.datapulse.etl;

import static io.datapulse.etl.IntegrationChannels.CHANNEL_FETCH_SALES;

import io.datapulse.domain.dto.SaleDto;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.raw.wb.WbSaleRaw;
import io.datapulse.marketplaces.mapper.wb.WbSaleMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.LoggingHandler.Level;
import reactor.core.publisher.Flux;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EtlFlow {

  private final WbAdapter wbAdapter;
  private final WbSaleMapper wbSaleMapper;

  @Bean
  public IntegrationFlow wbSalesFlow(TaskExecutor etlExecutor) {
    return IntegrationFlow.from(CHANNEL_FETCH_SALES)
        // ещё раз явно отцепимся на executor, чтобы весь downstream точно шёл не в HTTP-потоке
        .channel(MessageChannels.executor(etlExecutor))
        .handle(this::fetchWbSales)      // → Flux<WbSaleRaw>
        .split()                         // разбивает Flux на элементы
        .transform(wbSaleMapper::toDto)  // → SaleDto
        .handle(SaleDto.class, (sale, headers) -> {
          System.out.println("=====================" + formatLine(sale));
          return null;
        })
        .log(Level.INFO, "wbSalesFlow", m -> "✓ WB sales printed")
        .get();
  }

  private Flux<WbSaleRaw> fetchWbSales(Object payload, Map<String, Object> headers) {
    long accountId = (payload instanceof Long l) ? l
        : Long.parseLong(String.valueOf(payload));

    LocalDate from = (LocalDate) headers.getOrDefault("from", LocalDate.now().minusDays(1));
    LocalDate to = (LocalDate) headers.getOrDefault("to", LocalDate.now().minusDays(1));

    return wbAdapter.fetchSales(accountId, from, to)
        .timeout(Duration.ofMinutes(3))
        .doOnSubscribe(s -> log.info("WB fetchSales acc={} {}..{}", accountId, from, to))
        .doOnComplete(() -> log.info("WB fetchSales done acc={} {}..{}", accountId, from, to));
  }

  private static String formatLine(SaleDto s) {
    return String.format(
        "WB SALE | sku=%s | qty=%d | revenue=%s | priceFinal=%s | posting=%s | date=%s",
        nz(s.getSku()), nzI(s.getQuantity()), nzBD(s.getRevenue()), nzBD(s.getPriceFinal()),
        nz(s.getPostingNumber()), s.getEventDate()
    );
  }

  private static String nz(String v) {
    return v == null ? "" : v;
  }

  private static int nzI(Integer v) {
    return v == null ? 0 : v;
  }

  private static String nzBD(java.math.BigDecimal v) {
    return v == null ? "0.00" : v.toPlainString();
  }
}
