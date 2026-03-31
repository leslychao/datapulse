package io.datapulse.etl.domain;

import java.time.OffsetDateTime;
import java.util.List;

import io.datapulse.etl.config.RetentionProperties;
import io.datapulse.etl.config.S3Properties;
import io.datapulse.etl.persistence.JobItemRepository;
import io.datapulse.etl.persistence.JobItemRow;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles raw layer retention cleanup:
 * <ul>
 *   <li>Finance (FACT_FINANCE): time-based, 12 months</li>
 *   <li>Flow (SALES_FACT, ADVERTISING_FACT): time-based, 6 months</li>
 *   <li>State (dictionaries, snapshots): keep_count=3 per (connection, event)</li>
 * </ul>
 * Deletes S3 objects and marks job_item status as EXPIRED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private static final int BATCH_SIZE = 500;

    private final MinioClient minioClient;
    private final S3Properties s3Properties;
    private final RetentionProperties retentionProperties;
    private final JobItemRepository jobItemRepository;

    public void runRetention() {
        int financeExpired = cleanupTimeBased(
                "raw/%/FACT_FINANCE/%",
                retentionProperties.financeMonths(),
                "finance"
        );

        int flowExpired = 0;
        for (EtlEventType event : EtlEventType.flowEvents()) {
            flowExpired += cleanupTimeBased(
                    "raw/%/" + event.name() + "/%",
                    retentionProperties.flowMonths(),
                    "flow/" + event.name()
            );
        }

        int stateExpired = 0;
        for (EtlEventType event : EtlEventType.stateEvents()) {
            stateExpired += cleanupStateKeepCount(
                    "raw/%/" + event.name() + "/%",
                    retentionProperties.stateKeepCount(),
                    "state/" + event.name()
            );
        }

        log.info("Retention completed: financeExpired={}, flowExpired={}, stateExpired={}",
                financeExpired, flowExpired, stateExpired);
    }

    private int cleanupTimeBased(String eventPattern, int months, String label) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(months);
        int totalExpired = 0;

        List<JobItemRow> batch;
        do {
            batch = jobItemRepository.findForTimeRetention(cutoff, eventPattern, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            List<Long> expiredIds = deleteFromS3AndCollectIds(batch, label);
            jobItemRepository.markExpired(expiredIds);
            totalExpired += expiredIds.size();

        } while (batch.size() == BATCH_SIZE);

        return totalExpired;
    }

    private int cleanupStateKeepCount(String eventPattern, int keepCount, String label) {
        int totalExpired = 0;

        List<JobItemRow> batch;
        do {
            batch = jobItemRepository.findExcessStateItems(eventPattern, keepCount, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            List<Long> expiredIds = deleteFromS3AndCollectIds(batch, label);
            jobItemRepository.markExpired(expiredIds);
            totalExpired += expiredIds.size();

        } while (batch.size() == BATCH_SIZE);

        return totalExpired;
    }

    private List<Long> deleteFromS3AndCollectIds(List<JobItemRow> items, String label) {
        return items.stream()
                .filter(item -> deleteS3Object(item.getS3Key(), label))
                .map(JobItemRow::getId)
                .toList();
    }

    private boolean deleteS3Object(String s3Key, String label) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(s3Properties.rawBucket())
                    .object(s3Key)
                    .build());
            return true;
        } catch (Exception e) {
            log.error("Failed to delete S3 object: s3Key={}, category={}", s3Key, label, e);
            return false;
        }
    }
}
