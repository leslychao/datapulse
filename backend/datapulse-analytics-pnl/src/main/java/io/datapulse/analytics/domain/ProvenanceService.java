package io.datapulse.analytics.domain;

import io.datapulse.analytics.api.ProvenanceEntryResponse;
import io.datapulse.analytics.api.ProvenanceRawResponse;
import io.datapulse.analytics.persistence.ProvenanceRepository;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.storage.RawStorageUrlProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceService {

    private final ProvenanceRepository provenanceRepository;
    private final RawStorageUrlProvider rawStorageUrlProvider;

    public ProvenanceEntryResponse getCanonicalEntry(long entryId, long workspaceId) {
        return provenanceRepository.findCanonicalEntry(entryId, workspaceId)
                .orElseThrow(() -> NotFoundException.entity("canonical_finance_entry", entryId));
    }

    public ProvenanceRawResponse getRawUrl(long entryId, long workspaceId) {
        ProvenanceEntryResponse entry = getCanonicalEntry(entryId, workspaceId);

        if (entry.jobExecutionId() == null) {
            throw NotFoundException.entity("job_execution", "null (no provenance for entry " + entryId + ")");
        }

        ProvenanceRepository.RawFileInfo rawFile = provenanceRepository
                .findRawFileInfo(entry.jobExecutionId(), workspaceId)
                .orElseThrow(() -> {
                    log.warn("Raw data expired or not found: entryId={}, jobExecutionId={}",
                            entryId, entry.jobExecutionId());
                    return NotFoundException.entity("raw_file",
                            "expired (jobExecutionId=" + entry.jobExecutionId() + ")");
                });

        String presignedUrl = rawStorageUrlProvider.generatePresignedUrl(rawFile.s3Key());

        return new ProvenanceRawResponse(presignedUrl, rawFile.s3Key(), rawFile.byteSize());
    }
}
