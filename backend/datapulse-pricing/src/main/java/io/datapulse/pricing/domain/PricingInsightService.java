package io.datapulse.pricing.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.persistence.PricingInsightEntity;
import io.datapulse.pricing.persistence.PricingInsightEntity.InsightSeverity;
import io.datapulse.pricing.persistence.PricingInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingInsightService {

  private final PricingInsightRepository insightRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public Page<PricingInsightEntity> listInsights(long workspaceId,
                                                 String insightType,
                                                 Boolean acknowledged,
                                                 Pageable pageable) {
    if (insightType != null) {
      return insightRepository.findByType(workspaceId, insightType, pageable);
    }
    if (acknowledged != null && !acknowledged) {
      return insightRepository.findUnacknowledged(workspaceId, pageable);
    }
    return insightRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable);
  }

  @Transactional
  public void acknowledge(long insightId, long workspaceId) {
    PricingInsightEntity insight = insightRepository
        .findByIdAndWorkspaceId(insightId, workspaceId)
        .orElseThrow(() -> NotFoundException.entity("PricingInsight", insightId));

    insight.setAcknowledged(true);
    insightRepository.save(insight);
    log.info("Insight acknowledged: id={}, workspace={}", insightId, workspaceId);
  }

  @Transactional(readOnly = true)
  public long countUnacknowledged(long workspaceId) {
    return insightRepository.countByWorkspaceIdAndAcknowledgedFalse(workspaceId);
  }

  @Transactional
  public PricingInsightEntity createInsight(long workspaceId,
                                            InsightType type,
                                            String title,
                                            String body,
                                            InsightSeverity severity) {
    PricingInsightEntity entity = new PricingInsightEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setInsightType(type.name());
    entity.setTitle(title);
    entity.setBody(body);
    entity.setSeverity(severity);
    entity.setAcknowledged(false);
    PricingInsightEntity saved = insightRepository.save(entity);

    eventPublisher.publishEvent(new InsightCreatedEvent(
        saved.getId(), workspaceId, type.name(), title, severity.name()));

    log.info("Pricing insight created: id={}, workspace={}, type={}, severity={}",
        saved.getId(), workspaceId, type, severity);
    return saved;
  }
}
