package io.datapulse.pricing.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.pricing.api.CreateManualLockRequest;
import io.datapulse.pricing.api.ManualLockMapper;
import io.datapulse.pricing.api.ManualLockResponse;
import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import io.datapulse.pricing.persistence.ManualPriceLockRepository;
import io.datapulse.pricing.persistence.PricingRunReadRepository;
import io.datapulse.pricing.persistence.PricingRunReadRepository.OfferInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPriceLockService {

    private final ManualPriceLockRepository lockRepository;
    private final ManualLockMapper lockMapper;
    private final PricingRunReadRepository runReadRepository;

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

        ManualLockResponse response = lockMapper.toResponse(saved);
        Page<ManualLockResponse> enriched = enrichLocks(
                new PageImpl<>(List.of(response)));
        return enriched.getContent().get(0);
    }

    @Transactional(readOnly = true)
    public Page<ManualLockResponse> listActiveLocks(
        long workspaceId, Long marketplaceOfferId,
        Long connectionId, String search, Pageable pageable) {
        Page<ManualLockResponse> result;

        if (marketplaceOfferId != null) {
            List<ManualLockResponse> content = lockRepository.findActiveLock(marketplaceOfferId)
                .map(lockMapper::toResponse)
                .map(List::of)
                .orElse(List.of());
            result = new PageImpl<>(content, pageable, content.size());
        } else if (connectionId != null || (search != null && !search.isBlank())) {
            result = lockRepository.findActiveLocksFiltered(
                    workspaceId, connectionId, search, pageable)
                .map(lockMapper::toResponse);
        } else {
            result = lockRepository.findAllByWorkspaceIdAndUnlockedAtIsNull(
                    workspaceId, pageable)
                .map(lockMapper::toResponse);
        }

        return enrichLocks(result);
    }

    private Page<ManualLockResponse> enrichLocks(Page<ManualLockResponse> page) {
        if (page.isEmpty()) {
            return page;
        }

        Set<Long> offerIds = page.stream()
                .map(ManualLockResponse::marketplaceOfferId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> userIds = page.stream()
                .map(ManualLockResponse::lockedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, OfferInfo> offerInfoMap = runReadRepository.findOfferInfo(offerIds);
        Map<Long, String> userNames = runReadRepository.findUserNames(userIds);

        Set<Long> connectionIds = offerInfoMap.values().stream()
                .map(OfferInfo::connectionId)
                .collect(Collectors.toSet());
        Map<Long, String> connectionNames = runReadRepository.findConnectionNames(connectionIds);

        return page.map(l -> {
            OfferInfo offer = offerInfoMap.get(l.marketplaceOfferId());
            Long connId = offer != null ? offer.connectionId() : null;
            String connName = connId != null ? connectionNames.get(connId) : null;
            String userName = userNames.get(l.lockedBy());

            return new ManualLockResponse(
                    l.id(), l.marketplaceOfferId(), l.lockedPrice(),
                    l.reason(), l.lockedBy(), l.lockedAt(),
                    l.expiresAt(), l.unlockedAt(), l.unlockedBy(),
                    offer != null ? offer.name() : null,
                    offer != null ? offer.sellerSku() : null,
                    connId,
                    connName,
                    userName);
        });
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
