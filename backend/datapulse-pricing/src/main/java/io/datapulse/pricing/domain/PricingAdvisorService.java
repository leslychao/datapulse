package io.datapulse.pricing.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.datapulse.pricing.persistence.PriceDecisionEntity;
import io.datapulse.pricing.persistence.PriceDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingAdvisorService {

  private static final Duration CACHE_TTL = Duration.ofHours(24);

  private final PriceDecisionRepository decisionRepository;

  private final Cache<String, AdvisorResult> advisorCache = Caffeine.newBuilder()
      .maximumSize(5_000)
      .expireAfterWrite(CACHE_TTL)
      .build();

  public AdvisorResult generateAdvice(long offerId, long workspaceId) {
    Optional<PriceDecisionEntity> lastDecision =
        decisionRepository.findTopByMarketplaceOfferIdAndWorkspaceIdOrderByCreatedAtDesc(
            offerId, workspaceId);

    String cacheKey = buildCacheKey(offerId, lastDecision.map(PriceDecisionEntity::getId).orElse(0L));

    AdvisorResult cached = advisorCache.getIfPresent(cacheKey);
    if (cached != null) {
      log.debug("Advisor cache hit: offerId={}, cacheKey={}", offerId, cacheKey);
      return cached;
    }

    AdvisorResult result = callLlmAdvisor(offerId, workspaceId, lastDecision.orElse(null));
    if (result.advice() != null) {
      advisorCache.put(cacheKey, result);
    }
    return result;
  }

  private AdvisorResult callLlmAdvisor(long offerId, long workspaceId,
                                       PriceDecisionEntity lastDecision) {
    // TODO: Replace with real vLLM call when infrastructure is available.
    //
    // System prompt:
    //   "Ты — pricing advisor для селлеров маркетплейсов.
    //    Проанализируй данные и дай короткую рекомендацию (2-3 предложения).
    //    Формат: [Рекомендация] + [Обоснование] + [Риски].
    //    Не принимай решений, только советуй. Будь конкретен — цифры, проценты."
    //
    // User prompt context:
    //   - lastDecision.explanationSummary, signalSnapshot, constraintsApplied, guardsEvaluated
    //   - P&L data from mart_product_pnl (last period)
    //   - Inventory data from mart_inventory_analysis
    //   - Competitor data (if available)

    try {
      return buildMockAdvice(offerId, lastDecision);
    } catch (Exception e) {
      log.error("LLM advisor call failed: offerId={}, error={}", offerId, e.getMessage(), e);
      return AdvisorResult.unavailable();
    }
  }

  private AdvisorResult buildMockAdvice(long offerId, PriceDecisionEntity lastDecision) {
    if (lastDecision == null) {
      return AdvisorResult.unavailable();
    }

    OffsetDateTime now = OffsetDateTime.now();
    String mockAdvice = String.format(
        "По товару #%d последнее решение: %s. "
            + "Рекомендуется проверить текущую маржу и конкурентные цены перед следующим прогоном. "
            + "Риски: при снижении цены возможно падение маржинальности ниже целевого уровня.",
        offerId, lastDecision.getDecisionType());

    return new AdvisorResult(mockAdvice, now, now.plus(CACHE_TTL));
  }

  private String buildCacheKey(long offerId, long lastDecisionId) {
    return offerId + ":" + lastDecisionId;
  }

  public record AdvisorResult(
      String advice,
      OffsetDateTime generatedAt,
      OffsetDateTime cachedUntil
  ) {

    public static AdvisorResult unavailable() {
      return new AdvisorResult(null, null, null);
    }

    public boolean isAvailable() {
      return advice != null;
    }
  }
}
