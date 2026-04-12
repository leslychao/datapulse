package io.datapulse.etl.domain.source.wb;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.datapulse.etl.adapter.wb.WbFinanceReadAdapter;
import io.datapulse.etl.adapter.wb.WbNormalizer;
import io.datapulse.etl.adapter.wb.dto.WbFinanceRow;
import io.datapulse.etl.domain.CanonicalFinanceNormalizer;
import io.datapulse.etl.domain.CaptureContextFactory;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.EtlEventType;
import io.datapulse.etl.domain.EventSource;
import io.datapulse.etl.domain.FinanceEntryType;
import io.datapulse.etl.domain.IngestContext;
import io.datapulse.etl.domain.normalized.NormalizedFinanceItem;
import io.datapulse.etl.domain.SubSourceResult;
import io.datapulse.etl.domain.SubSourceRunner;
import io.datapulse.etl.persistence.canonical.CanonicalFinanceEntryUpsertRepository;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WbFinanceFactSource implements EventSource {

  private final WbFinanceReadAdapter adapter;
  private final WbNormalizer normalizer;
  private final CanonicalFinanceEntryUpsertRepository repository;
  private final CanonicalFinanceNormalizer financeNormalizer;
  private final SubSourceRunner subSourceRunner;

  @Override
  public MarketplaceType marketplace() {
    return MarketplaceType.WB;
  }

  @Override
  public EtlEventType eventType() {
    return EtlEventType.FACT_FINANCE;
  }

  @Override
  public List<SubSourceResult> execute(IngestContext ctx) {
    String token = ctx.credentials().get(CredentialKeys.WB_API_TOKEN);
    var captureCtx = CaptureContextFactory.build(ctx, eventType(), "WbFinanceReadAdapter");
    List<CaptureResult> pages = adapter.captureAllPages(
        captureCtx, token, ctx.wbFactDateFrom(), ctx.wbFactDateTo());

    Set<String> unmappedTypes = ConcurrentHashMap.newKeySet();

    SubSourceResult result = subSourceRunner.processPages(
        "WbFinanceReadAdapter", pages, WbFinanceRow.class,
        batch -> {
          var normalized = batch.stream()
              .map(row -> {
                NormalizedFinanceItem item = normalizer.normalizeFinance(row);
                if (item.entryType() == FinanceEntryType.OTHER
                    && row.supplierOperName() != null
                    && !row.supplierOperName().isBlank()) {
                  unmappedTypes.add(row.supplierOperName().trim());
                }
                return item;
              })
              .toList();
          repository.batchUpsert(financeNormalizer.normalizeBatch(normalized, ctx));
        });

    if (!unmappedTypes.isEmpty()) {
      return List.of(SubSourceResult.successWithWarnings(
          result.sourceId(), result.pagesProcessed(),
          result.recordsProcessed(), unmappedTypes));
    }
    return List.of(result);
  }
}
