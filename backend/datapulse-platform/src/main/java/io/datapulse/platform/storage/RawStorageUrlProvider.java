package io.datapulse.platform.storage;

/**
 * Generates presigned URLs for S3 raw file access.
 * Used by analytics provenance drill-down to provide direct S3 links.
 */
public interface RawStorageUrlProvider {

    String generatePresignedUrl(String s3Key);
}
