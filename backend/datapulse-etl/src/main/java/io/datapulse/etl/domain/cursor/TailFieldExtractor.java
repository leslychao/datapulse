package io.datapulse.etl.domain.cursor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For WB Finance {@code rrdid}-based cursor (1 endpoint: reportDetailByPeriod).
 * Reads the last 32 KB of the temp file and finds the last occurrence of {@code "rrd_id"}.
 * Overhead: < 1 ms (direct seek + regex on small tail chunk).
 */
public final class TailFieldExtractor implements CursorExtractor {

    private static final int TAIL_SIZE = 32 * 1024;

    private final Pattern fieldPattern;

    public TailFieldExtractor(String fieldName) {
        this.fieldPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)");
    }

    /**
     * Creates extractor for WB Finance {@code rrd_id} field.
     */
    public static TailFieldExtractor wbRrdId() {
        return new TailFieldExtractor("rrd_id");
    }

    @Override
    public Optional<String> extract(Path tempFile) throws IOException {
        String tail = readTail(tempFile);
        return findLastMatch(tail);
    }

    private String readTail(Path tempFile) throws IOException {
        try (var raf = new RandomAccessFile(tempFile.toFile(), "r")) {
            long fileSize = raf.length();
            long offset = Math.max(0, fileSize - TAIL_SIZE);
            int readSize = (int) Math.min(TAIL_SIZE, fileSize);

            raf.seek(offset);
            byte[] buffer = new byte[readSize];
            raf.readFully(buffer);

            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    private Optional<String> findLastMatch(String tail) {
        Matcher matcher = fieldPattern.matcher(tail);
        String lastValue = null;

        while (matcher.find()) {
            lastValue = matcher.group(1);
        }

        return Optional.ofNullable(lastValue);
    }
}
