package io.datapulse.etl.domain;

/**
 * @param listRequestOffset numeric list offset (Ozon Seller list {@code offset}) for this request
 * @param listResumeKey opaque resume key for this request (e.g. Ozon {@code last_id}, finance
 *     {@code page}, product-info batch index as decimal string)
 */
public record CaptureResult(
        long jobItemId,
        String s3Key,
        String contentSha256,
        long byteSize,
        Long listRequestOffset,
        String listResumeKey
) {

  public CaptureResult(long jobItemId, String s3Key, String contentSha256, long byteSize) {
    this(jobItemId, s3Key, contentSha256, byteSize, null, null);
  }
}
