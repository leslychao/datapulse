package io.datapulse.etl.adapter.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.datapulse.etl.domain.CaptureContext;
import io.datapulse.etl.domain.CaptureResult;
import io.datapulse.etl.domain.PageCaptureResult;
import io.datapulse.etl.domain.S3RawStorage;
import io.datapulse.etl.domain.cursor.CursorExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Orchestrates the full page capture pipeline:
 * <ol>
 *   <li>Stream {@code Flux<DataBuffer>} → temp file (64 KB, SHA-256)</li>
 *   <li>Extract pagination cursor from temp file</li>
 *   <li>Upload temp file to S3 + insert job_item</li>
 *   <li>Delete temp file</li>
 * </ol>
 * Memory footprint: 64 KB per page, regardless of response size.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingPageCapture {

    private final StreamingResponseWriter responseWriter;
    private final S3RawStorage s3RawStorage;

    public PageCaptureResult capture(Flux<DataBuffer> responseBody,
                                     CaptureContext context,
                                     int pageNumber,
                                     CursorExtractor cursorExtractor) {
        TempFileWriteResult writeResult = responseWriter.writeToTempFile(
                responseBody, context.requestId(), pageNumber);

        try {
            String cursor = cursorExtractor.extract(writeResult.path()).orElse(null);

            CaptureResult captureResult = s3RawStorage.captureFromFile(
                    writeResult.path(), writeResult.sha256(), writeResult.byteSize(),
                    context, pageNumber);

            log.debug("Page captured: requestId={}, page={}, cursor={}, byteSize={}",
                    context.requestId(), pageNumber, cursor, writeResult.byteSize());

            return new PageCaptureResult(captureResult, cursor);
        } catch (Exception e) {
            deleteTempFileSilently(writeResult.path());
            throw new IllegalStateException(
                    "Failed to capture page: requestId=%s, page=%d"
                            .formatted(context.requestId(), pageNumber), e);
        }
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
}
