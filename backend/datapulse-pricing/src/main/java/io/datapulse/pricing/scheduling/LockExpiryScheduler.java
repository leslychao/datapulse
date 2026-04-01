package io.datapulse.pricing.scheduling;

import io.datapulse.pricing.persistence.ManualPriceLockEntity;
import io.datapulse.pricing.persistence.ManualPriceLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockExpiryScheduler {

    private final ManualPriceLockRepository lockRepository;

    @Scheduled(cron = "${datapulse.pricing.lock-expiry-cron:0 0 * * * *}")
    @Transactional
    public void expireLocks() {
        try {
            List<ManualPriceLockEntity> expired = lockRepository.findExpiredLocks();
            if (expired.isEmpty()) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now();
            for (ManualPriceLockEntity lock : expired) {
                lock.setUnlockedAt(now);
                lock.setUnlockedBy(null);
            }
            lockRepository.saveAll(expired);

            log.info("Expired {} manual price locks", expired.size());
        } catch (Exception e) {
            log.error("Lock expiry job failed", e);
        }
    }
}
