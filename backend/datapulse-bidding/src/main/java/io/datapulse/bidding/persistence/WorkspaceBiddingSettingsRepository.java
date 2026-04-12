package io.datapulse.bidding.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceBiddingSettingsRepository
    extends JpaRepository<WorkspaceBiddingSettingsEntity, Long> {

  Optional<WorkspaceBiddingSettingsEntity> findByWorkspaceId(Long workspaceId);
}
