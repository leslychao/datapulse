package io.datapulse.pricing.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.CreateManualLockRequest;
import io.datapulse.pricing.api.ManualLockMapper;
import io.datapulse.pricing.api.ManualLockResponse;
import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import io.datapulse.pricing.persistence.ManualPriceLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPriceLockService {

    private final ManualPriceLockRepository lockRepository;
    private final ManualLockMapper lockMapper;

    @Transactional
    public ManualLockResponse createLock(CreateManualLockRequest request,
                                         long workspaceId, long userId) {
        boolean alreadyLocked = lockRepository.isLocked(request.marketplaceOfferId());
        if (alreadyLocked) {
            throw BadRequestException.of(MessageCodes.PRICING_LOCK_ALREADY_EXISTS);
        }

        var entity = new ManualPriceLockEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setMarketplaceOfferId(request.marketplaceOfferId());
        entity.setLockedPrice(request.lockedPrice());
        entity.setReason(request.reason());
        entity.setLockedBy(userId);
        entity.setExpiresAt(request.expiresAt());

        ManualPriceLockEntity saved = lockRepository.save(entity);
        log.info("Manual price lock created: id={}, offerId={}, workspaceId={}",
                saved.getId(), request.marketplaceOfferId(), workspaceId);

        return lockMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ManualLockResponse> listActiveLocks(
        long workspaceId, Long marketplaceOfferId, Pageable pageable) {
        if (marketplaceOfferId != null) {
            List<ManualLockResponse> content = lockRepository.findActiveLock(marketplaceOfferId)
                .map(lockMapper::toResponse)
                .map(List::of)
                .orElse(List.of());
            return new PageImpl<>(content, pageable, content.size());
        }

        return lockRepository.findAllByWorkspaceIdAndUnlockedAtIsNull(workspaceId, pageable)
            .map(lockMapper::toResponse);
    }

    @Transactional
    public void unlock(long lockId, long workspaceId, long userId) {
        ManualPriceLockEntity entity = lockRepository.findById(lockId)
                .orElseThrow(() -> NotFoundException.entity("ManualPriceLock", lockId));

        if (!entity.getWorkspaceId().equals(workspaceId)) {
            throw NotFoundException.entity("ManualPriceLock", lockId);
        }

        doUnlock(entity, userId);
    }

    @Transactional
    public void unlockByOfferId(long offerId, long workspaceId, long userId) {
        ManualPriceLockEntity entity = lockRepository.findActiveLock(offerId)
                .orElseThrow(() -> NotFoundException.of(MessageCodes.PRICING_LOCK_NOT_FOUND));

        if (!entity.getWorkspaceId().equals(workspaceId)) {
            throw NotFoundException.of(MessageCodes.PRICING_LOCK_NOT_FOUND);
        }

        doUnlock(entity, userId);
    }

    private void doUnlock(ManualPriceLockEntity entity, long userId) {
        if (entity.getUnlockedAt() != null) {
            return;
        }

        entity.setUnlockedAt(OffsetDateTime.now());
        entity.setUnlockedBy(userId);
        lockRepository.save(entity);

        log.info("Manual price lock removed: id={}, offerId={}, unlockedBy={}",
                entity.getId(), entity.getMarketplaceOfferId(), userId);
    }
}
