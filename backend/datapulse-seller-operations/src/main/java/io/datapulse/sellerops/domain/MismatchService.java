package io.datapulse.sellerops.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.MismatchFilter;
import io.datapulse.sellerops.api.MismatchResponse;
import io.datapulse.sellerops.api.MismatchSummaryResponse;
import io.datapulse.sellerops.persistence.MismatchJdbcRepository;
import io.datapulse.sellerops.persistence.MismatchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MismatchService {

  private final MismatchJdbcRepository mismatchRepository;

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
