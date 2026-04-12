package io.datapulse.execution.domain;

import io.datapulse.execution.persistence.SimulatedOfferStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages simulation shadow-state lifecycle.
 * Shadow-state is never deleted automatically — only through explicit operator reset.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final SimulatedOfferStateRepository simulatedStateRepository;

    @Transactional
    public int resetShadowState(long workspaceId, String sourcePlatform) {
        int deleted = simulatedStateRepository.deleteBySourcePlatform(
                workspaceId, sourcePlatform);
        log.info("Shadow-state reset: workspaceId={}, sourcePlatform={}, deletedRows={}",
                workspaceId, sourcePlatform, deleted);
        return deleted;
    }
}
