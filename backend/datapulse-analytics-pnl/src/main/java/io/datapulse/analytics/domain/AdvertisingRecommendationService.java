package io.datapulse.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.api.CrossMpComparisonResponse;
import io.datapulse.analytics.api.ProductAdRecommendationResponse;
import io.datapulse.analytics.persistence.AdvertisingClickHouseReadRepository;
import io.datapulse.analytics.persistence.CategoryAdAvg;
import io.datapulse.analytics.persistence.CrossMpAdComparison;
import io.datapulse.analytics.persistence.OfferAdMetrics;
import io.datapulse.analytics.persistence.RecommendationOfferReadRepository;
import io.datapulse.analytics.persistence.RecommendationOfferRow;
import io.datapulse.analytics.persistence.WorkspaceConnectionRepository;
import io.datapulse.common.error.MessageCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvertisingRecommendationService {

  private static final int MIN_AD_DAYS = 7;
  private static final BigDecimal DRR_BUFFER_PP = BigDecimal.valueOf(5);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final WorkspaceConnectionRepository connectionRepository;
  private final RecommendationOfferReadRepository offerRepo;
  private final AdvertisingClickHouseReadRepository chRepo;

  public List<CrossMpComparisonResponse> getCrossMarketplaceComparison(
      long workspaceId, List<Long> sellerSkuIds) {

    List<Long> connectionIds =
        connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
    if (connectionIds.isEmpty() || sellerSkuIds.isEmpty()) {
      return List.of();
    }

    return chRepo.findCrossMarketplaceComparison(connectionIds, sellerSkuIds)
        .stream()
        .map(row -> new CrossMpComparisonResponse(
            row.sellerSkuId(), row.sourcePlatform(),
            row.spend(), row.drrPct(), row.roas(),
            row.cpo(), row.cpc(), row.crPct()))
        .toList();
  }

  public List<ProductAdRecommendationResponse> getRecommendations(
      long workspaceId, List<Long> offerIds) {

    List<Long> connectionIds =
        connectionRepository.findConnectionIdsByWorkspaceId(workspaceId);
    if (connectionIds.isEmpty() || offerIds.isEmpty()) {
      return List.of();
    }

    List<RecommendationOfferRow> offers =
        offerRepo.findOfferData(workspaceId, offerIds);
    if (offers.isEmpty()) {
      return List.of();
    }

    List<Long> foundOfferIds = offers.stream()
        .map(RecommendationOfferRow::offerId).toList();
    Map<Long, OfferAdMetrics> adMetrics =
        chRepo.findOfferAdMetrics(connectionIds, foundOfferIds);

    List<String> categories = offers.stream()
        .map(RecommendationOfferRow::category)
        .filter(c -> c != null && !c.isBlank())
        .distinct()
        .toList();
    Map<String, CategoryAdAvg> categoryAvgs =
        chRepo.findCategoryAvgMetrics(connectionIds, categories);

    List<ProductAdRecommendationResponse> results = new ArrayList<>();
    for (RecommendationOfferRow offer : offers) {
      results.add(evaluate(offer, adMetrics.get(offer.offerId()),
          categoryAvgs.get(offer.category())));
    }
    return results;
  }

  private ProductAdRecommendationResponse evaluate(
      RecommendationOfferRow offer,
      OfferAdMetrics adMetrics,
      CategoryAdAvg categoryAvg) {

    BigDecimal marginPct = offer.marginPct();
    BigDecimal currentPrice = offer.currentPrice();

    if (marginPct == null || currentPrice == null
        || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
      return buildResponse(offer.offerId(), AdRecommendation.INSUFFICIENT_DATA,
          marginPct, null, null, null,
          MessageCodes.AD_RECOMMENDATION_INSUFFICIENT_DATA);
    }

    boolean hasAdData = adMetrics != null && adMetrics.adDays() >= MIN_AD_DAYS;
    boolean hasCategoryData = categoryAvg != null
        && categoryAvg.avgCpc() != null
        && categoryAvg.avgCr() != null
        && categoryAvg.avgCr().compareTo(BigDecimal.ZERO) > 0;

    if (!hasAdData && !hasCategoryData) {
      return buildResponse(offer.offerId(), AdRecommendation.INSUFFICIENT_DATA,
          marginPct, null, null, null,
          MessageCodes.AD_RECOMMENDATION_INSUFFICIENT_DATA);
    }

    BigDecimal currentDrrPct = hasAdData ? adMetrics.currentDrrPct() : null;
    BigDecimal estimatedDrrPct = computeEstimatedDrr(
        categoryAvg, currentPrice);
    BigDecimal maxCpc = computeMaxCpc(
        currentPrice, marginPct, categoryAvg);

    if (currentDrrPct != null && marginPct.compareTo(currentDrrPct) < 0) {
      return buildResponse(offer.offerId(),
          AdRecommendation.NOT_WORTH_ADVERTISING,
          marginPct, currentDrrPct, estimatedDrrPct, maxCpc,
          MessageCodes.AD_RECOMMENDATION_NOT_WORTH);
    }

    if (hasAdData && maxCpc != null && adMetrics.avgCpc() != null
        && adMetrics.avgCpc().compareTo(maxCpc) > 0) {
      return buildResponse(offer.offerId(), AdRecommendation.REDUCE_BID,
          marginPct, currentDrrPct, estimatedDrrPct, maxCpc,
          MessageCodes.AD_RECOMMENDATION_REDUCE_BID);
    }

    if (estimatedDrrPct != null
        && marginPct.compareTo(estimatedDrrPct.add(DRR_BUFFER_PP)) > 0) {
      return buildResponse(offer.offerId(),
          AdRecommendation.WORTH_ADVERTISING,
          marginPct, currentDrrPct, estimatedDrrPct, maxCpc,
          MessageCodes.AD_RECOMMENDATION_WORTH);
    }

    if (estimatedDrrPct == null && currentDrrPct == null) {
      return buildResponse(offer.offerId(), AdRecommendation.INSUFFICIENT_DATA,
          marginPct, null, null, maxCpc,
          MessageCodes.AD_RECOMMENDATION_INSUFFICIENT_DATA);
    }

    return buildResponse(offer.offerId(), AdRecommendation.WORTH_ADVERTISING,
        marginPct, currentDrrPct, estimatedDrrPct, maxCpc,
        MessageCodes.AD_RECOMMENDATION_WORTH);
  }

  /**
   * estimated DRR = avg_cpc / (avg_cr × price) × 100.
   * Represents the expected DRR given category-average CPC and CR at the product's price point.
   */
  private BigDecimal computeEstimatedDrr(
      CategoryAdAvg categoryAvg, BigDecimal price) {

    if (categoryAvg == null
        || categoryAvg.avgCpc() == null
        || categoryAvg.avgCr() == null
        || categoryAvg.avgCr().compareTo(BigDecimal.ZERO) == 0
        || price.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    BigDecimal denominator = categoryAvg.avgCr().multiply(price);
    return categoryAvg.avgCpc()
        .divide(denominator, 6, RoundingMode.HALF_UP)
        .multiply(HUNDRED)
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * max CPC = price × (margin_pct / 100) × avg_cr.
   * The highest affordable bid that keeps ad cost within margin.
   */
  private BigDecimal computeMaxCpc(
      BigDecimal price, BigDecimal marginPct, CategoryAdAvg categoryAvg) {

    if (categoryAvg == null
        || categoryAvg.avgCr() == null
        || categoryAvg.avgCr().compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    BigDecimal targetDrr = marginPct.divide(HUNDRED, 6, RoundingMode.HALF_UP);
    return price.multiply(targetDrr)
        .multiply(categoryAvg.avgCr())
        .setScale(2, RoundingMode.HALF_UP);
  }

  private ProductAdRecommendationResponse buildResponse(
      long offerId, AdRecommendation recommendation,
      BigDecimal marginPct, BigDecimal currentDrrPct,
      BigDecimal estimatedDrrPct, BigDecimal maxCpc,
      String reasoning) {

    return new ProductAdRecommendationResponse(
        offerId, recommendation, marginPct,
        currentDrrPct, estimatedDrrPct, maxCpc, reasoning);
  }
}
