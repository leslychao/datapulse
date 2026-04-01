package io.datapulse.etl.adapter.s3;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.datapulse.etl.config.EtlProperties;
import io.datapulse.etl.config.S3Properties;
import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureRequest;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.persistence.JobItemRepository;
import io.datapulse.etl.persistence.JobItemRow;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3RawStorage {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final MinioClient minioClient;
    private final S3Properties s3Properties;
    private final EtlProperties etlProperties;
    private final JobItemRepository jobItemRepository;

    /**
     * Accepts an already-written temp file (from {@link io.datapulse.etl.adapter.util.StreamingResponseWriter}).
     * Uploads to S3, inserts job_item, deletes temp file. No double disk-write.
     */
    public CaptureResult captureFromFile(Path existingTempFile, String sha256, long byteSize,
                                         CaptureContext context, int pageNumber) {
        try {
            String s3Key = buildS3Key(
                    context.connectionId(), context.etlEvent().name(),
                    context.sourceId(), context.requestId(), pageNumber
            );

            uploadToS3(s3Key, existingTempFile, byteSize);

            long jobItemId = insertJobItemFromContext(context, s3Key, sha256, byteSize, pageNumber);

            log.info("Raw page captured: s3Key={}, byteSize={}, sha256={}",
                    s3Key, byteSize, sha256);

            return new CaptureResult(jobItemId, s3Key, sha256, byteSize);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to capture from file: requestId=%s, page=%d"
                            .formatted(context.requestId(), pageNumber), e);
        } finally {
            deleteTempFileSilently(existingTempFile);
        }
    }

    public CaptureResult capture(CaptureRequest request) {
        Path tempFile = null;
        try {
            tempFile = createTempFile(request.requestId(), request.pageNumber());

            String sha256 = writeToTempFile(request.responseBody(), tempFile);
            long byteSize = Files.size(tempFile);

            String s3Key = buildS3Key(
                    request.connectionId(), request.etlEvent().name(),
                    request.sourceId(), request.requestId(), request.pageNumber()
            );

            uploadToS3(s3Key, tempFile, byteSize);

            long jobItemId = insertJobItem(request, s3Key, sha256, byteSize);

            log.info("Raw page captured: s3Key={}, byteSize={}, sha256={}",
                    s3Key, byteSize, sha256);

            return new CaptureResult(jobItemId, s3Key, sha256, byteSize);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to capture raw page: requestId=%s, page=%d"
                    .formatted(request.requestId(), request.pageNumber()), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to capture raw page: requestId=%s, page=%d"
                    .formatted(request.requestId(), request.pageNumber()), e);
        } finally {
            deleteTempFileSilently(tempFile);
        }
    }

    private Path createTempFile(String requestId, int pageNumber) throws IOException {
        Path tempDir = Path.of(etlProperties.tempDir());
        Files.createDirectories(tempDir);
        return Files.createTempFile(tempDir, "raw-%s-p%d-".formatted(requestId, pageNumber), ".json");
    }

    private String writeToTempFile(InputStream source, Path tempFile) throws IOException {
        MessageDigest digest = createSha256Digest();

        try (var digestStream = new DigestInputStream(new BufferedInputStream(source, BUFFER_SIZE), digest);
             OutputStream out = Files.newOutputStream(tempFile)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = digestStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private void uploadToS3(String s3Key, Path tempFile, long byteSize) throws Exception {
        try (InputStream fileStream = new BufferedInputStream(Files.newInputStream(tempFile), BUFFER_SIZE)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(s3Properties.rawBucket())
                    .object(s3Key)
                    .stream(fileStream, byteSize, -1L)
                    .contentType("application/json")
                    .build());
        }
    }

    private long insertJobItem(CaptureRequest request, String s3Key, String sha256, long byteSize) {
        var row = JobItemRow.builder()
                .jobExecutionId(request.jobExecutionId())
                .requestId(request.requestId())
                .sourceId(request.sourceId())
                .pageNumber(request.pageNumber())
                .s3Key(s3Key)
                .contentSha256(sha256)
                .byteSize(byteSize)
                .build();

        return jobItemRepository.insert(row);
    }

    private long insertJobItemFromContext(CaptureContext context, String s3Key,
                                          String sha256, long byteSize, int pageNumber) {
        var row = JobItemRow.builder()
                .jobExecutionId(context.jobExecutionId())
                .requestId(context.requestId())
                .sourceId(context.sourceId())
                .pageNumber(pageNumber)
                .s3Key(s3Key)
                .contentSha256(sha256)
                .byteSize(byteSize)
                .build();

        return jobItemRepository.insert(row);
    }

    private String buildS3Key(long connectionId, String event, String sourceId,
                              String requestId, int pageNumber) {
        return "raw/%d/%s/%s/%s/page-%d.json"
                .formatted(connectionId, event, sourceId, requestId, pageNumber);
    }

    private void deleteTempFileSilently(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: path={}", tempFile, e);
        }
    }

    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
