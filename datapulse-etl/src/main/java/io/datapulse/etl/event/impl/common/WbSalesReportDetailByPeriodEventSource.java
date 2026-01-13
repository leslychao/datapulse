package io.datapulse.etl.event.impl.common;

import static io.datapulse.etl.MarketplaceEvent.FACT_FINANCE;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.MarketplaceEvent;
import io.datapulse.etl.RawTableNames;
import io.datapulse.etl.event.EtlSourceMeta;
import io.datapulse.etl.event.EventSource;
import io.datapulse.marketplaces.adapter.WbAdapter;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.dto.raw.sales.WbSalesReportDetailRowRaw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@EtlSourceMeta(
    events = {FACT_FINANCE},
    marketplace = MarketplaceType.WILDBERRIES,
    rawTableName = RawTableNames.RAW_WB_SALES_REPORT_DETAIL
)
public class WbSalesReportDetailByPeriodEventSource implements EventSource {

  private static final int PAGE_LIMIT = 100000;
  private static final String PERIOD = "weekly";

  private final WbAdapter wbAdapter;

  @Override
  public List<Snapshot<?>> fetchSnapshots(
      long accountId,
      MarketplaceEvent event,
      LocalDate dateFrom,
      LocalDate dateTo
  ) {
    List<Snapshot<?>> snapshots = new ArrayList<>();

    long rrdid = 0L;

    while (true) {
      Snapshot<WbSalesReportDetailRowRaw> snapshot =
          wbAdapter.downloadSalesReportDetailByPeriodPage(
              accountId,
              dateFrom,
              dateTo,
              rrdid,
              PAGE_LIMIT,
              PERIOD
          );

      snapshots.add(snapshot);

      String next = snapshot.nextToken();
      if (next == null) {
        break;
      }
      if (next.equals(String.valueOf(rrdid))) {
        break;
      }

      rrdid = Long.parseLong(next);
    }

    return snapshots;
  }
}
