package io.datapulse.sellerops.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.MismatchDetailResponse;
import io.datapulse.sellerops.api.MismatchDetailResponse.OfferInfo;
import io.datapulse.sellerops.api.MismatchDetailResponse.RelatedAction;
import io.datapulse.sellerops.api.MismatchDetailResponse.Thresholds;
import io.datapulse.sellerops.api.MismatchDetailResponse.TimelineEvent;
import io.datapulse.sellerops.api.MismatchFilter;
import io.datapulse.sellerops.api.MismatchResponse;
import io.datapulse.sellerops.api.MismatchSummaryResponse;
import io.datapulse.sellerops.api.MismatchSummaryResponse.TimelinePoint;
import io.datapulse.sellerops.api.MismatchSummaryResponse.TypeDistribution;
import io.datapulse.sellerops.config.MismatchProperties;
import io.datapulse.sellerops.persistence.MismatchDetailRow;
import io.datapulse.sellerops.persistence.MismatchJdbcRepository;
import io.datapulse.sellerops.persistence.MismatchJdbcRepository.SummaryData;
import io.datapulse.sellerops.persistence.MismatchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MismatchService {

  private final MismatchJdbcRepository mismatchRepository;
  private final MismatchProperties mismatchProperties;
  private final MismatchStompPublisher stompPublisher;

  @Transactional(readOnly = true)
  public MismatchSummaryResponse getSummary(long workspaceId) {
    SummaryData data = mismatchRepository.getSummaryData(workspaceId);
    List<TypeDistribution> distribution = mismatchRepository.getDistributionByType(workspaceId);
    List<TimelinePoint> timeline = mismatchRepository.getTimeline(workspaceId, 14);

    long activeDelta = data.activeLast7d() - data.activePrev7d();
    long criticalDelta = data.criticalLast7d() - data.criticalPrev7d();

    BigDecimal avgHoursDelta = BigDecimal.ZERO;

    return new MismatchSummaryResponse(
        data.activeCount(),
        activeDelta,
        data.criticalCount(),
        criticalDelta,
        data.avgHoursUnresolved() != null
            ? data.avgHoursUnresolved().setScale(1, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO,
        avgHoursDelta,
        data.autoResolvedToday(),
        data.autoResolvedYesterday(),
        distribution,
        timeline
    );
  }

  @Transactional(readOnly = true)
  public MismatchDetailResponse getMismatchDetail(long workspaceId, long mismatchId) {
    MismatchDetailRow row = mismatchRepository.findDetailById(mismatchId, workspaceId)
        .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));

    return toDetailResponse(row);
  }

  @Transactional(readOnly = true)
  public Page<MismatchResponse> listMismatches(long workspaceId,
                                                MismatchFilter filter,
                                                Pageable pageable) {
    Page<MismatchRow> page = mismatchRepository.findAll(workspaceId, filter, pageable);
    List<MismatchResponse> responses = page.getContent().stream()
        .map(this::toResponse)
        .toList();
    return new PageImpl<>(responses, pageable, page.getTotalElements());
  }

  @Transactional
  public MismatchResponse acknowledge(long workspaceId, long mismatchId, long userId) {
    int updated = mismatchRepository.acknowledge(mismatchId, workspaceId, userId);
    if (updated == 0) {
      mismatchRepository.findById(mismatchId, workspaceId)
          .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
      throw BadRequestException.of(MessageCodes.ALERT_EVENT_INVALID_STATE);
    }
    MismatchResponse response = mismatchRepository.findById(mismatchId, workspaceId)
        .map(this::toResponse)
        .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));

    stompPublisher.publishAcknowledged(workspaceId, mismatchId,
        response.type(), response.severity(),
        response.offerName(), response.deltaPct());

    return response;
  }

  @Transactional
  public MismatchResponse resolve(long workspaceId, long mismatchId,
                                   String resolution, String note, long userId) {
    try {
      MismatchResolutionType.valueOf(resolution);
    } catch (IllegalArgumentException e) {
      throw BadRequestException.of(MessageCodes.MISMATCH_INVALID_RESOLUTION);
    }

    int updated = mismatchRepository.resolve(mismatchId, workspaceId,
        resolution, note, userId);
    if (updated == 0) {
      mismatchRepository.findById(mismatchId, workspaceId)
          .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
      throw BadRequestException.of(MessageCodes.ALERT_EVENT_INVALID_STATE);
    }
    MismatchResponse response = mismatchRepository.findById(mismatchId, workspaceId)
        .map(this::toResponse)
        .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));

    if (MismatchResolutionType.IGNORED.name().equals(resolution)) {
      stompPublisher.publishIgnored(workspaceId, mismatchId,
          response.type(), response.severity(),
          response.offerName(), response.deltaPct());
    } else {
      stompPublisher.publishResolved(workspaceId, mismatchId,
          response.type(), response.severity(),
          response.offerName(), response.deltaPct());
    }

    return response;
  }

  @Transactional
  public int bulkAcknowledge(long workspaceId, List<Long> ids, long userId) {
    return mismatchRepository.bulkAcknowledge(workspaceId, ids, userId);
  }

  @Transactional
  public int bulkIgnore(long workspaceId, List<Long> ids, String reason, long userId) {
    return mismatchRepository.bulkIgnore(workspaceId, ids, reason, userId);
  }

  @Transactional(readOnly = true)
  public List<MismatchRow> findAllForExport(long workspaceId, MismatchFilter filter) {
    return mismatchRepository.findAllForExport(workspaceId, filter);
  }

  private MismatchDetailResponse toDetailResponse(MismatchDetailRow row) {
    var offer = new OfferInfo(
        row.getOfferId() != null ? row.getOfferId() : 0,
        row.getOfferName(),
        row.getSkuCode(),
        row.getMarketplaceType(),
        row.getConnectionName()
    );

    RelatedAction relatedAction = null;
    if (row.getRelatedActionId() != null) {
      relatedAction = new RelatedAction(
          row.getRelatedActionId(),
          row.getRelatedActionStatus(),
          row.getRelatedActionTargetPrice(),
          row.getRelatedActionExecutedAt(),
          row.getRelatedActionReconciliationSource()
      );
    }

    var thresholds = new Thresholds(
        mismatchProperties.getPriceWarningThresholdPct(),
        mismatchProperties.getPriceCriticalThresholdPct()
    );

    String expectedSource = resolveExpectedSource(row);
    String actualSource = resolveActualSource(row);
    String apiStatus = MismatchStatus.toApi(row.getStatus(), row.getResolvedReason());
    String resolution = extractResolution(row.getResolvedReason());
    String note = extractNote(row.getResolvedReason());
    List<TimelineEvent> timeline = buildTimeline(row, apiStatus);

    return new MismatchDetailResponse(
        row.getAlertEventId(),
        row.getMismatchType(),
        row.getSeverity(),
        apiStatus,
        row.getExpectedValue(),
        row.getActualValue(),
        row.getDeltaPct(),
        row.getDetectedAt(),
        row.getRelatedActionId(),
        offer,
        expectedSource,
        actualSource,
        row.getAcknowledgedAt(),
        row.getAcknowledgedByName(),
        row.getResolvedByName(),
        row.getResolvedAt(),
        resolution,
        note,
        relatedAction,
        thresholds,
        timeline
    );
  }

  private String resolveExpectedSource(MismatchDetailRow row) {
    if ("PRICE".equals(row.getMismatchType())) {
      return "price_action (last succeeded)";
    }
    return "system";
  }

  private String resolveActualSource(MismatchDetailRow row) {
    if ("PRICE".equals(row.getMismatchType())) {
      return "marketplace_api (canonical_price_current)";
    }
    return "marketplace_api";
  }

  private List<TimelineEvent> buildTimeline(MismatchDetailRow row, String apiStatus) {
    var events = new ArrayList<TimelineEvent>();

    events.add(new TimelineEvent(
        "DETECTED",
        row.getDetectedAt(),
        "mismatches.timeline.detected",
        "system"
    ));

    if (row.getAcknowledgedAt() != null) {
      events.add(new TimelineEvent(
          "ACKNOWLEDGED",
          row.getAcknowledgedAt(),
          "mismatches.timeline.acknowledged",
          row.getAcknowledgedByName() != null
              ? row.getAcknowledgedByName() : "system"
      ));
    }

    if (row.getResolvedAt() != null) {
      String descKey = MismatchStatus.IGNORED.equals(apiStatus)
          ? "mismatches.timeline.ignored"
          : "mismatches.timeline.resolved";

      events.add(new TimelineEvent(
          "RESOLVED",
          row.getResolvedAt(),
          descKey,
          row.getResolvedByName() != null
              ? row.getResolvedByName() : "system"
      ));
    }

    return events;
  }

  private MismatchResponse toResponse(MismatchRow row) {
    String apiStatus = MismatchStatus.toApi(row.getStatus(), row.getResolvedReason());
    String resolution = extractResolution(row.getResolvedReason());

    return new MismatchResponse(
        row.getAlertEventId(),
        row.getMismatchType(),
        row.getOfferId(),
        row.getOfferName(),
        row.getSkuCode(),
        row.getMarketplaceType(),
        row.getConnectionName(),
        row.getExpectedValue(),
        row.getActualValue(),
        row.getDeltaPct(),
        row.getSeverity(),
        apiStatus,
        resolution,
        row.getDetectedAt(),
        row.getResolvedAt(),
        row.getRelatedActionId()
    );
  }

  private String extractResolution(String resolvedReason) {
    if (resolvedReason == null) {
      return null;
    }
    int colonIndex = resolvedReason.indexOf(": ");
    return colonIndex > 0 ? resolvedReason.substring(0, colonIndex) : resolvedReason;
  }

  private String extractNote(String resolvedReason) {
    if (resolvedReason == null) {
      return null;
    }
    int colonIndex = resolvedReason.indexOf(": ");
    return colonIndex > 0 ? resolvedReason.substring(colonIndex + 2) : null;
  }
}
