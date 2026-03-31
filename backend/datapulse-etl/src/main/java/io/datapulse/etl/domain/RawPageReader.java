package io.datapulse.etl.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.config.S3Properties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads raw JSON pages from S3 using Jackson streaming API.
 * Memory footprint bounded by batch size (~1.5 MB at batch=500).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RawPageReader {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final MinioClient minioClient;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;

    public <T> void readBatched(String s3Key, Class<T> recordType, Consumer<List<T>> batchConsumer) {
        readBatched(s3Key, recordType, DEFAULT_BATCH_SIZE, batchConsumer);
    }

    public <T> void readBatched(String s3Key, Class<T> recordType, int batchSize,
                                Consumer<List<T>> batchConsumer) {
        try (InputStream s3Stream = openS3Stream(s3Key);
             JsonParser parser = objectMapper.getFactory().createParser(s3Stream)) {

            skipToArrayContent(parser);

            List<T> batch = new ArrayList<>(batchSize);
            int totalRecords = 0;

            while (parser.nextToken() != JsonToken.END_ARRAY && parser.currentToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    T record = objectMapper.readValue(parser, recordType);
                    batch.add(record);
                    totalRecords++;

                    if (batch.size() >= batchSize) {
                        batchConsumer.accept(List.copyOf(batch));
                        batch.clear();
                    }
                }
            }

            if (!batch.isEmpty()) {
                batchConsumer.accept(List.copyOf(batch));
            }

            log.debug("Read completed: s3Key={}, totalRecords={}", s3Key, totalRecords);

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read raw page: s3Key=%s".formatted(s3Key), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read raw page: s3Key=%s".formatted(s3Key), e);
        }
    }

    /**
     * Returns the total number of records in the page without deserializing objects.
     */
    public int countRecords(String s3Key) {
        try (InputStream s3Stream = openS3Stream(s3Key);
             JsonParser parser = objectMapper.getFactory().createParser(s3Stream)) {

            skipToArrayContent(parser);

            int count = 0;
            int depth = 0;

            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.START_OBJECT) {
                    if (depth == 0) {
                        count++;
                    }
                    depth++;
                } else if (token == JsonToken.END_OBJECT) {
                    depth--;
                } else if (token == JsonToken.END_ARRAY && depth == 0) {
                    break;
                }
            }

            return count;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to count records: s3Key=%s".formatted(s3Key), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to count records: s3Key=%s".formatted(s3Key), e);
        }
    }

    private InputStream openS3Stream(String s3Key) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(s3Properties.rawBucket())
                .object(s3Key)
                .build());
    }

    /**
     * Advances the parser to the first element inside the root JSON array.
     * Handles both root-level arrays {@code [...]} and wrapper objects like
     * {@code {"data": [...], "cursor": "..."}} by finding the first array token.
     */
    private void skipToArrayContent(JsonParser parser) throws IOException {
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                return;
            }
        }
        throw new IOException("No JSON array found in response");
    }
}
