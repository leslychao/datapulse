package io.datapulse.bidding.domain;

import java.math.BigDecimal;

import io.datapulse.bidding.persistence.WorkspaceBiddingSettingsEntity;
import io.datapulse.bidding.persistence.WorkspaceBiddingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceBiddingSettingsService {

  private final WorkspaceBiddingSettingsRepository repository;

  @Transactional(readOnly = true)
  public WorkspaceBiddingSettingsEntity getSettings(long workspaceId) {
    return repository.findByWorkspaceId(workspaceId)
        .orElseGet(() -> defaultSettings(workspaceId));
  }

  @Transactional
  public WorkspaceBiddingSettingsEntity updateSettings(
      long workspaceId,
      boolean biddingEnabled,
      BigDecimal maxAggregateDailySpend,
      int minDecisionIntervalHours) {
    var entity = repository.findByWorkspaceId(workspaceId)
        .orElseGet(() -> {
          var fresh = new WorkspaceBiddingSettingsEntity();
          fresh.setWorkspaceId(workspaceId);
          return fresh;
        });
    entity.setBiddingEnabled(biddingEnabled);
    entity.setMaxAggregateDailySpend(maxAggregateDailySpend);
    entity.setMinDecisionIntervalHours(minDecisionIntervalHours);
    return repository.save(entity);
  }

  @Transactional(readOnly = true)
  public boolean isBiddingEnabled(long workspaceId) {
    return repository.findByWorkspaceId(workspaceId)
        .map(WorkspaceBiddingSettingsEntity::isBiddingEnabled)
        .orElse(true);
  }

  private WorkspaceBiddingSettingsEntity defaultSettings(long workspaceId) {
    var entity = new WorkspaceBiddingSettingsEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setBiddingEnabled(true);
    entity.setMaxAggregateDailySpend(null);
    entity.setMinDecisionIntervalHours(4);
    return entity;
  }
}
