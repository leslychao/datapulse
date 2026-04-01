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
import io.datapulse.sellerops.config.MismatchProperties;
import io.datapulse.sellerops.persistence.MismatchDetailRow;
import io.datapulse.sellerops.persistence.MismatchJdbcRepository;
import io.datapulse.sellerops.persistence.MismatchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MismatchService {

  private final MismatchJdbcRepository mismatchRepository;
  private final MismatchProperties mismatchProperties;

  @Transactional(readOnly = true)
  public MismatchSummaryResponse getSummary(long workspaceId) {
    var counts = mismatchRepository.countByStatus(workspaceId);
    return new MismatchSummaryResponse(
        counts.total(), counts.open(), counts.acknowledged(), counts.resolved());
  }

  @Transactional(readOnly = true)
  public MismatchResponse getMismatch(long workspaceId, long mismatchId) {
    return mismatchRepository.findById(mismatchId, workspaceId)
        .map(this::toResponse)
        .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
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
      MismatchRow row = mismatchRepository.findById(mismatchId, workspaceId)
          .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
      throw BadRequestException.of(
          MessageCodes.ALERT_EVENT_INVALID_STATE, row.getStatus());
    }
    return mismatchRepository.findById(mismatchId, workspaceId)
        .map(this::toResponse)
        .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
  }

  @Transactional
  public MismatchResponse resolve(long workspaceId, long mismatchId,
                                   String resolution, String note) {
    MismatchResolutionType.valueOf(resolution);

    int updated = mismatchRepository.resolve(mismatchId, workspaceId, resolution);
    if (updated == 0) {
      MismatchRow row = mismatchRepository.findById(mismatchId, workspaceId)
          .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
      throw BadRequestException.of(
          MessageCodes.ALERT_EVENT_INVALID_STATE, row.getStatus());
    }
    return mismatchRepository.findById(mismatchId, workspaceId)
        .map(this::toResponse)
        .orElseThrow(() -> NotFoundException.entity("mismatch", mismatchId));
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

    List<TimelineEvent> timeline = buildTimeline(row);

    return new MismatchDetailResponse(
        row.getAlertEventId(),
        row.getMismatchType(),
        row.getSeverity(),
        row.getStatus(),
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
        row.getResolvedReason(),
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

  private List<TimelineEvent> buildTimeline(MismatchDetailRow row) {
    var events = new ArrayList<TimelineEvent>();

    events.add(new TimelineEvent(
        "DETECTED",
        row.getDetectedAt(),
        "Mismatch detected: expected %s, actual %s".formatted(
            row.getExpectedValue(), row.getActualValue()),
        "system"
    ));

    if (row.getAcknowledgedAt() != null) {
      events.add(new TimelineEvent(
          "ACKNOWLEDGED",
          row.getAcknowledgedAt(),
          "Mismatch acknowledged",
          row.getAcknowledgedByName() != null
              ? row.getAcknowledgedByName() : "unknown"
      ));
    }

    if (row.getResolvedAt() != null) {
      String description = "Mismatch resolved";
      if (row.getResolvedReason() != null) {
        description = "Mismatch resolved: %s".formatted(row.getResolvedReason());
      }
      events.add(new TimelineEvent(
          "RESOLVED",
          row.getResolvedAt(),
          description,
          row.getResolvedByName() != null
              ? row.getResolvedByName() : "system"
      ));
    }

    return events;
  }

  private MismatchResponse toResponse(MismatchRow row) {
    return new MismatchResponse(
        row.getAlertEventId(),
        row.getMismatchType(),
        row.getOfferId(),
        row.getOfferName(),
        row.getSkuCode(),
        row.getExpectedValue(),
        row.getActualValue(),
        row.getDeltaPct(),
        row.getSeverity(),
        row.getStatus(),
        row.getDetectedAt(),
        row.getConnectionName()
    );
  }
}
