package io.datapulse.etl.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.datapulse.etl.persistence.JobItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Processes already-captured raw pages: reads from S3, normalizes via batch streaming,
 * upserts to canonical layer, and marks job_items as PROCESSED.
 *
 * <p>This is the "post-capture" processor — adapters handle the API fetch and raw capture,
 * then hand off {@link CaptureResult} pages to this runner for normalization and persistence.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubSourceRunner {

    private final RawPageReader rawPageReader;
    private final JobItemRepository jobItemRepository;

    /**
     * Processes a list of captured pages through the normalize→UPSERT pipeline.
     *
     * @param sourceId        logical source identifier (adapter class name)
     * @param capturedPages   pages already captured in S3 (from adapter)
     * @param recordType      Jackson DTO class for deserialization from S3
     * @param batchProcessor  callback: receives a batch of deserialized records, normalizes, and UPSERTs
     * @param <T>             provider DTO type
     * @return sub-source result with processing stats
     */
    public <T> SubSourceResult processPages(String sourceId,
                                            List<CaptureResult> capturedPages,
                                            Class<T> recordType,
                                            Consumer<List<T>> batchProcessor) {
        int totalRecordsProcessed = 0;
        int totalRecordsSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (CaptureResult page : capturedPages) {
            try {
                int[] counts = processOnePage(page, recordType, batchProcessor);
                totalRecordsProcessed += counts[0];
                totalRecordsSkipped += counts[1];

                jobItemRepository.updateStatus(page.jobItemId(), JobItemStatus.PROCESSED);
            } catch (Exception e) {
                log.error("Page processing failed: sourceId={}, s3Key={}, error={}",
                        sourceId, page.s3Key(), e.getMessage(), e);
                errors.add("Page %s: %s".formatted(page.s3Key(), e.getMessage()));

                jobItemRepository.updateStatus(page.jobItemId(), JobItemStatus.FAILED);
            }
        }

        if (!errors.isEmpty() && totalRecordsProcessed == 0) {
            return SubSourceResult.failed(sourceId, errors.get(0));
        }
        if (!errors.isEmpty()) {
            return SubSourceResult.partial(sourceId, null,
                    capturedPages.size(), totalRecordsProcessed, totalRecordsSkipped, errors);
        }

        return SubSourceResult.success(sourceId, capturedPages.size(), totalRecordsProcessed);
    }

    /**
     * Processes a single page: stream from S3 → batch deserialize → callback.
     *
     * @return int[2]: [recordsProcessed, recordsSkipped]
     */
    private <T> int[] processOnePage(CaptureResult page, Class<T> recordType,
                                     Consumer<List<T>> batchProcessor) {
        int[] counts = {0, 0};

        rawPageReader.readBatched(page.s3Key(), recordType, batch -> {
            try {
                batchProcessor.accept(batch);
                counts[0] += batch.size();
            } catch (Exception e) {
                log.warn("Batch processing error: s3Key={}, batchSize={}, error={}",
                        page.s3Key(), batch.size(), e.getMessage());
                counts[1] += batch.size();
            }
        });

        return counts;
    }
}
