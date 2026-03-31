package io.datapulse.etl.adapter.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import io.datapulse.etl.config.EtlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Streams WebClient response body ({@link Flux}<{@link DataBuffer}>)
 * to a temp file on disk with 64 KB buffer. Computes SHA-256 on the fly.
 * Memory footprint: 64 KB regardless of response size (WB Finance can be 300 MB).
 * <p>
 * R-CAP-01 mitigation: every {@link DataBuffer} is released in {@code finally}
 * via {@link DataBufferUtils#release}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingResponseWriter {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final EtlProperties etlProperties;

    public TempFileWriteResult writeToTempFile(Flux<DataBuffer> body,
                                               String requestId, int pageNumber) {
        Path tempFile = createTempFile(requestId, pageNumber);
        MessageDigest digest = createSha256Digest();
        AtomicReference<IOException> writeError = new AtomicReference<>();

        try (OutputStream out = new DigestOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tempFile), BUFFER_SIZE),
                digest)) {

            body.publishOn(Schedulers.boundedElastic())
                    .doOnNext(buffer -> {
                        try {
                            int readable = buffer.readableByteCount();
                            byte[] bytes = new byte[readable];
                            buffer.read(bytes);
                            out.write(bytes);
                        } catch (IOException e) {
                            writeError.set(e);
                            throw new UncheckedIOException(e);
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                    .blockLast();

        } catch (IOException e) {
            deleteSilently(tempFile);
            throw new UncheckedIOException(
                    "Failed to write response to temp file: requestId=%s, page=%d"
                            .formatted(requestId, pageNumber), e);
        } catch (Exception e) {
            deleteSilently(tempFile);
            IOException ioErr = writeError.get();
            if (ioErr != null) {
                throw new UncheckedIOException(
                        "Failed to write response to temp file: requestId=%s, page=%d"
                                .formatted(requestId, pageNumber), ioErr);
            }
            throw new IllegalStateException(
                    "Failed to write response to temp file: requestId=%s, page=%d"
                            .formatted(requestId, pageNumber), e);
        }

        try {
            long byteSize = Files.size(tempFile);
            String sha256 = HexFormat.of().formatHex(digest.digest());

            log.debug("Response written to temp file: path={}, byteSize={}, sha256={}",
                    tempFile, byteSize, sha256);

            return new TempFileWriteResult(tempFile, sha256, byteSize);
        } catch (IOException e) {
            deleteSilently(tempFile);
            throw new UncheckedIOException("Failed to stat temp file", e);
        }
    }

    private Path createTempFile(String requestId, int pageNumber) {
        try {
            Path tempDir = Path.of(etlProperties.tempDir());
            Files.createDirectories(tempDir);
            return Files.createTempFile(tempDir,
                    "raw-%s-p%d-".formatted(requestId, pageNumber), ".json");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp file", e);
        }
    }

    private static void deleteSilently(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: path={}", file, e);
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
