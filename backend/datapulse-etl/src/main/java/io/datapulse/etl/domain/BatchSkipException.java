package io.datapulse.etl.domain;

import lombok.Getter;

@Getter
public class BatchSkipException extends RuntimeException {

  private final int processedCount;
  private final int skippedCount;

  public BatchSkipException(int processedCount, int skippedCount) {
    super("Batch processing partially failed: processed=%d, skipped=%d"
        .formatted(processedCount, skippedCount));
    this.processedCount = processedCount;
    this.skippedCount = skippedCount;
  }
}
