package io.datapulse.bidding.domain;

import io.datapulse.bidding.api.CreateManualBidLockRequest;
import io.datapulse.bidding.persistence.ManualBidLockEntity;
import io.datapulse.bidding.persistence.ManualBidLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ManualBidLockService {

  private final ManualBidLockRepository lockRepository;

  @Transactional
  public ManualBidLockEntity createLock(
      long workspaceId,
      long marketplaceOfferId,
      Integer lockedBid,
      String reason,
      Long lockedBy,
      OffsetDateTime expiresAt) {

    Optional<ManualBidLockEntity> existing = lockRepository
        .findByWorkspaceIdAndMarketplaceOfferId(workspaceId, marketplaceOfferId);

    if (existing.isPresent()) {
      ManualBidLockEntity lock = existing.get();
      lock.setLockedBid(lockedBid);
      lock.setReason(reason);
      lock.setLockedBy(lockedBy);
      lock.setExpiresAt(expiresAt);
      return lockRepository.save(lock);
    }

    var entity = new ManualBidLockEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setMarketplaceOfferId(marketplaceOfferId);
    entity.setLockedBid(lockedBid);
    entity.setReason(reason);
    entity.setLockedBy(lockedBy);
    entity.setExpiresAt(expiresAt);

    return lockRepository.save(entity);
  }

  @Transactional
  public void removeLock(long lockId) {
    lockRepository.deleteById(lockId);
  }

  @Transactional
  public List<ManualBidLockEntity> bulkCreateLock(
      long workspaceId,
      List<CreateManualBidLockRequest> requests,
      Long lockedBy) {

    List<ManualBidLockEntity> results = new ArrayList<>(requests.size());
    for (CreateManualBidLockRequest req : requests) {
      results.add(createLock(
          workspaceId,
          req.marketplaceOfferId(),
          req.lockedBid(),
          req.reason(),
          lockedBy,
          req.expiresAt()));
    }
    return results;
  }

  @Transactional
  public void bulkRemoveLock(List<Long> lockIds) {
    lockRepository.deleteAllById(lockIds);
  }

  @Transactional(readOnly = true)
  public Optional<ManualBidLockEntity> findActiveLock(long workspaceId, long marketplaceOfferId) {
    return lockRepository.findByWorkspaceIdAndMarketplaceOfferId(workspaceId, marketplaceOfferId);
  }
}
