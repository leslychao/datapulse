package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;

import io.datapulse.pricing.api.ImpactPreviewResponse.ImpactPreviewSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactNarrativeService {

  @Async
  public CompletableFuture<String> generateNarrative(ImpactPreviewSummary summary) {
    // TODO: Replace with real vLLM call when infrastructure is available.
    //
    // Prompt context: summary (totalOffers, changeCount, avgPriceChangePct, minMarginAfter)
    //                 + top-5 biggest changes + top-3 blocked by guards
    // Expected output: 3-5 sentences summarizing the impact.

    try {
      String narrative = buildMockNarrative(summary);
      return CompletableFuture.completedFuture(narrative);
    } catch (Exception e) {
      log.error("Narrative generation failed: {}", e.getMessage(), e);
      return CompletableFuture.completedFuture(null);
    }
  }

  private String buildMockNarrative(ImpactPreviewSummary summary) {
    if (summary.changeCount() == 0) {
      return String.format(
          "Политика затронет %d товаров, но ни один не требует изменения цены. "
              + "Все товары находятся в допустимом ценовом диапазоне.",
          summary.totalOffers());
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Политика затронет %d товаров. ", summary.totalOffers()));
    sb.append(String.format(
        "%d из %d товаров получат новую цену. ",
        summary.changeCount(), summary.eligibleCount()));

    if (summary.avgPriceChangePct() != null) {
      sb.append(String.format(
          "Среднее изменение цены составит %s%%. ",
          summary.avgPriceChangePct().setScale(1, RoundingMode.HALF_UP)));
    }
    if (summary.minMarginAfter() != null) {
      BigDecimal marginPct = summary.minMarginAfter()
          .multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP);
      sb.append(String.format(
          "Минимальная маржа после изменения — %s%%.", marginPct));
    }
    if (summary.skipCount() > 0) {
      sb.append(String.format(
          " %d товаров пропущены из-за блокирующих проверок.", summary.skipCount()));
    }

    return sb.toString();
  }
}
