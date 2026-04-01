package io.datapulse.etl.domain;

import java.util.concurrent.TimeUnit;

import io.datapulse.platform.storage.RawStorageUrlProvider;
import io.datapulse.etl.config.S3Properties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioRawStorageUrlProvider implements RawStorageUrlProvider {

    private static final int PRESIGNED_URL_EXPIRY_HOURS = 1;

    private final MinioClient minioClient;
    private final S3Properties s3Properties;

    @Override
    public String generatePresignedUrl(String s3Key) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(s3Properties.rawBucket())
                            .object(s3Key)
                            .method(Http.Method.GET)
                            .expiry(PRESIGNED_URL_EXPIRY_HOURS, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: s3Key={}", s3Key, e);
            throw new IllegalStateException("Failed to generate presigned URL for: " + s3Key, e);
        }
    }
}
