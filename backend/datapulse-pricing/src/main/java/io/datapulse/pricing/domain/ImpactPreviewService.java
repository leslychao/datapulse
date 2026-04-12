package io.datapulse.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.ImpactPreviewResponse;
import io.datapulse.pricing.api.ImpactPreviewResponse.ImpactPreviewOfferResponse;
import io.datapulse.pricing.api.ImpactPreviewResponse.ImpactPreviewSummary;
import io.datapulse.pricing.api.ImpactPreviewResponse.NarrativeStatus;
import io.datapulse.pricing.persistence.ImpactPreviewReadRepository;
import io.datapulse.pricing.persistence.ImpactPreviewRow;
import io.datapulse.pricing.persistence.PricePolicyEntity;
import io.datapulse.pricing.persistence.PricePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactPreviewService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal ONE = BigDecimal.ONE;

  private static final long NARRATIVE_TIMEOUT_SECONDS = 5;

  private final PricePolicyRepository policyRepository;
  private final ImpactPreviewReadRepository previewRepository;
  private final ImpactNarrativeService narrativeService;
  private final ObjectMapper objectMapper;

  @Transactional
  public ImpactPreviewResponse preview(long policyId, long workspaceId, Pageable pageable) {
    PricePolicyEntity policy = policyRepository.findByIdAndWorkspaceId(policyId, workspaceId)
        .orElseThrow(() -> NotFoundException.entity("PricePolicy", policyId));

    if (policy.getStatus() == PolicyStatus.ARCHIVED) {
      throw BadRequestException.of(MessageCodes.PRICING_POLICY_ARCHIVED);
    }

    List<ImpactPreviewRow> allRows = previewRepository.findPreviewOffers(policyId, workspaceId);
    List<EvaluatedOffer> evaluated = evaluateAll(allRows, policy);

    ImpactPreviewSummary summary = buildSummary(allRows.size(), evaluated);
    Page<ImpactPreviewOfferResponse> offersPage = paginateOffers(evaluated, pageable);

    policy.setLastPreviewVersion(policy.getVersion());
    policyRepository.save(policy);

    log.info("Impact preview: policyId={}, totalOffers={}, changes={}, skips={}, holds={}",
        policyId, summary.totalOffers(), summary.changeCount(),
        summary.skipCount(), summary.holdCount());

    CompletableFuture<String> narrativeFuture = narrativeService.generateNarrative(summary);
    return resolveNarrative(summary, offersPage, narrativeFuture);
  }

  private List<EvaluatedOffer> evaluateAll(List<ImpactPreviewRow> rows,
                                           PricePolicyEntity policy) {
    List<EvaluatedOffer> results = new ArrayList<>(rows.size());
    for (ImpactPreviewRow row : rows) {
      results.add(evaluateOffer(row, policy));
    }
    return results;
  }

  private EvaluatedOffer evaluateOffer(ImpactPreviewRow row, PricePolicyEntity policy) {
    if (row.hasManualLock()) {
      return EvaluatedOffer.skip(row, MessageCodes.PRICING_GUARD_MANUAL_LOCK);
    }
    if (!"ACTIVE".equals(row.offerStatus())) {
      return EvaluatedOffer.skip(row, MessageCodes.PRICING_PREVIEW_OFFER_INACTIVE);
    }
    if (row.currentPrice() == null) {
      return EvaluatedOffer.skip(row, MessageCodes.PRICING_CURRENT_PRICE_MISSING);
    }

    return switch (policy.getStrategyType()) {
      case TARGET_MARGIN -> evaluateTargetMargin(row, policy);
      case PRICE_CORRIDOR -> evaluatePriceCorridor(row, policy);
      case MANUAL_OVERRIDE -> EvaluatedOffer.skip(row, MessageCodes.PRICING_PREVIEW_MANUAL_OVERRIDE);
      case VELOCITY_ADAPTIVE, STOCK_BALANCING, COMPOSITE, COMPETITOR_ANCHOR ->
          EvaluatedOffer.hold(row, MessageCodes.PRICING_PREVIEW_NOT_SUPPORTED_FOR_STRATEGY);
    };
  }

  private EvaluatedOffer evaluateTargetMargin(ImpactPreviewRow row,
                                              PricePolicyEntity policy) {
    if (row.cogs() == null) {
      return EvaluatedOffer.hold(row, MessageCodes.PRICING_COGS_MISSING);
    }

    TargetMarginParams params = parseStrategyParams(
        policy.getStrategyParams(), TargetMarginParams.class);

    BigDecimal commissionPct = resolveCommissionPct(params);
    BigDecimal effectiveCostRate = commissionPct;

    BigDecimal targetMargin = params.targetMarginPct();
    BigDecimal denominator = ONE.subtract(targetMargin).subtract(effectiveCostRate);

    if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
      return EvaluatedOffer.skip(row, MessageCodes.PRICING_DENOMINATOR_INVALID);
    }

    BigDecimal rawPrice = row.cogs().divide(denominator, 2, RoundingMode.HALF_UP);
    BigDecimal targetPrice = applyConstraints(rawPrice, row.currentPrice(), policy, params);

    if (targetPrice.compareTo(row.currentPrice()) == 0) {
      return EvaluatedOffer.skip(row, MessageCodes.PRICING_NO_CHANGE);
    }

    BigDecimal margin = computeMarginAfter(targetPrice, row.cogs(), effectiveCostRate);
    return EvaluatedOffer.change(row, targetPrice, margin);
  }

  private EvaluatedOffer evaluatePriceCorridor(ImpactPreviewRow row,
                                               PricePolicyEntity policy) {
    PriceCorridorParams params = parseStrategyParams(
        policy.getStrategyParams(), PriceCorridorParams.class);

    BigDecimal currentPrice = row.currentPrice();
    BigDecimal targetPrice = currentPrice;

    if (params.minPrice() != null && currentPrice.compareTo(params.minPrice()) < 0) {
      targetPrice = params.minPrice();
    }
    if (params.maxPrice() != null && currentPrice.compareTo(params.maxPrice()) > 0) {
      targetPrice = params.maxPrice();
    }

    targetPrice = applyPolicyConstraints(targetPrice, currentPrice, policy);

    if (targetPrice.compareTo(currentPrice) == 0) {
      return EvaluatedOffer.skip(row, MessageCodes.PRICING_NO_CHANGE);
    }

    BigDecimal margin = row.cogs() != null
        ? computeSimpleMargin(targetPrice, row.cogs())
        : null;
    return EvaluatedOffer.change(row, targetPrice, margin);
  }

  private BigDecimal resolveCommissionPct(TargetMarginParams params) {
    if (params.commissionManualPct() != null) {
      return params.commissionManualPct();
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal applyConstraints(BigDecimal rawPrice, BigDecimal currentPrice,
                                      PricePolicyEntity policy, TargetMarginParams params) {
    BigDecimal price = rawPrice;
    price = applyPolicyConstraints(price, currentPrice, policy);
    price = applyRounding(price, params);
    return price;
  }

  private BigDecimal applyPolicyConstraints(BigDecimal price, BigDecimal currentPrice,
                                            PricePolicyEntity policy) {
    if (policy.getMinPrice() != null && price.compareTo(policy.getMinPrice()) < 0) {
      price = policy.getMinPrice();
    }
    if (policy.getMaxPrice() != null && price.compareTo(policy.getMaxPrice()) > 0) {
      price = policy.getMaxPrice();
    }
    if (policy.getMaxPriceChangePct() != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal maxDelta = currentPrice.multiply(policy.getMaxPriceChangePct());
      BigDecimal upperBound = currentPrice.add(maxDelta);
      BigDecimal lowerBound = currentPrice.subtract(maxDelta);
      if (price.compareTo(upperBound) > 0) {
        price = upperBound;
      }
      if (price.compareTo(lowerBound) < 0) {
        price = lowerBound;
      }
    }
    return price;
  }

  private BigDecimal applyRounding(BigDecimal price, TargetMarginParams params) {
    BigDecimal step = params.effectiveRoundingStep();
    return switch (params.effectiveRoundingDirection()) {
      case FLOOR -> price.divide(step, 0, RoundingMode.FLOOR).multiply(step);
      case CEIL -> price.divide(step, 0, RoundingMode.CEILING).multiply(step);
      case NEAREST -> price.divide(step, 0, RoundingMode.HALF_UP).multiply(step);
    };
  }

  private BigDecimal computeMarginAfter(BigDecimal targetPrice, BigDecimal cogs,
                                        BigDecimal effectiveCostRate) {
    if (targetPrice.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal revenue = targetPrice.multiply(ONE.subtract(effectiveCostRate));
    return revenue.subtract(cogs)
        .divide(revenue, 4, RoundingMode.HALF_UP);
  }

  private BigDecimal computeSimpleMargin(BigDecimal targetPrice, BigDecimal cogs) {
    if (targetPrice.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return targetPrice.subtract(cogs)
        .divide(targetPrice, 4, RoundingMode.HALF_UP);
  }

  private ImpactPreviewSummary buildSummary(int totalOffers, List<EvaluatedOffer> evaluated) {
    int eligibleCount = 0;
    int changeCount = 0;
    int skipCount = 0;
    int holdCount = 0;
    BigDecimal totalChangePct = BigDecimal.ZERO;
    BigDecimal maxChangePct = null;
    BigDecimal minMargin = null;

    for (EvaluatedOffer offer : evaluated) {
      switch (offer.decisionType) {
        case "CHANGE" -> {
          eligibleCount++;
          changeCount++;
          if (offer.changePct != null) {
            BigDecimal absPct = offer.changePct.abs();
            totalChangePct = totalChangePct.add(absPct);
            if (maxChangePct == null || absPct.compareTo(maxChangePct) > 0) {
              maxChangePct = absPct;
            }
          }
          if (offer.marginAfter != null
              && (minMargin == null || offer.marginAfter.compareTo(minMargin) < 0)) {
            minMargin = offer.marginAfter;
          }
        }
        case "SKIP" -> {
          skipCount++;
          if (!isEligibilitySkip(offer.skipReason)) {
            eligibleCount++;
          }
        }
        case "HOLD" -> {
          holdCount++;
        }
      }
    }

    BigDecimal avgChangePct = changeCount > 0
        ? totalChangePct.divide(BigDecimal.valueOf(changeCount), 2, RoundingMode.HALF_UP)
        : null;

    return new ImpactPreviewSummary(
        totalOffers, eligibleCount, changeCount, skipCount, holdCount,
        avgChangePct, maxChangePct, minMargin);
  }

  private boolean isEligibilitySkip(String skipReason) {
    return MessageCodes.PRICING_GUARD_MANUAL_LOCK.equals(skipReason)
        || MessageCodes.PRICING_PREVIEW_OFFER_INACTIVE.equals(skipReason)
        || MessageCodes.PRICING_CURRENT_PRICE_MISSING.equals(skipReason);
  }

  private Page<ImpactPreviewOfferResponse> paginateOffers(List<EvaluatedOffer> evaluated,
                                                          Pageable pageable) {
    evaluated.sort(Comparator
        .comparing((EvaluatedOffer o) -> sortPriorityForDecision(o.decisionType))
        .thenComparing(o -> o.changePct != null ? o.changePct.abs() : BigDecimal.ZERO,
            Comparator.reverseOrder()));

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), evaluated.size());
    List<ImpactPreviewOfferResponse> pageContent;

    if (start >= evaluated.size()) {
      pageContent = List.of();
    } else {
      pageContent = evaluated.subList(start, end).stream()
          .map(EvaluatedOffer::toResponse)
          .toList();
    }

    return new PageImpl<>(pageContent, pageable, evaluated.size());
  }

  private int sortPriorityForDecision(String decisionType) {
    return switch (decisionType) {
      case "CHANGE" -> 0;
      case "HOLD" -> 1;
      case "SKIP" -> 2;
      default -> 3;
    };
  }

  private ImpactPreviewResponse resolveNarrative(
      ImpactPreviewSummary summary,
      Page<ImpactPreviewOfferResponse> offersPage,
      CompletableFuture<String> narrativeFuture) {
    try {
      String narrative = narrativeFuture.get(NARRATIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (narrative != null) {
        return new ImpactPreviewResponse(summary, offersPage,
            narrative, NarrativeStatus.READY);
      }
    } catch (Exception e) {
      log.debug("Narrative generation timed out or failed: {}", e.getMessage());
    }
    return new ImpactPreviewResponse(summary, offersPage,
        null, NarrativeStatus.UNAVAILABLE);
  }

  private <T> T parseStrategyParams(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw BadRequestException.of(MessageCodes.VALIDATION_FAILED, e, "strategyParams");
    }
  }

  private static class EvaluatedOffer {

    final ImpactPreviewRow row;
    final String decisionType;
    final BigDecimal targetPrice;
    final BigDecimal changePct;
    final BigDecimal changeAmount;
    final BigDecimal marginAfter;
    final String skipReason;

    private EvaluatedOffer(ImpactPreviewRow row, String decisionType,
                           BigDecimal targetPrice, BigDecimal marginAfter,
                           String skipReason) {
      this.row = row;
      this.decisionType = decisionType;
      this.targetPrice = targetPrice;
      this.marginAfter = marginAfter;
      this.skipReason = skipReason;

      if (targetPrice != null && row.currentPrice() != null
          && row.currentPrice().compareTo(BigDecimal.ZERO) > 0) {
        this.changeAmount = targetPrice.subtract(row.currentPrice());
        this.changePct = changeAmount
            .divide(row.currentPrice(), 4, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(1, RoundingMode.HALF_UP);
      } else {
        this.changeAmount = null;
        this.changePct = null;
      }
    }

    static EvaluatedOffer change(ImpactPreviewRow row, BigDecimal targetPrice,
                                 BigDecimal marginAfter) {
      return new EvaluatedOffer(row, "CHANGE", targetPrice, marginAfter, null);
    }

    static EvaluatedOffer skip(ImpactPreviewRow row, String skipReason) {
      return new EvaluatedOffer(row, "SKIP", null, null, skipReason);
    }

    static EvaluatedOffer hold(ImpactPreviewRow row, String holdReason) {
      return new EvaluatedOffer(row, "HOLD", null, null, holdReason);
    }

    ImpactPreviewOfferResponse toResponse() {
      return new ImpactPreviewOfferResponse(
          row.offerName(),
          row.sellerSku(),
          row.currentPrice(),
          targetPrice,
          changePct,
          changeAmount,
          decisionType,
          skipReason);
    }
  }
}
