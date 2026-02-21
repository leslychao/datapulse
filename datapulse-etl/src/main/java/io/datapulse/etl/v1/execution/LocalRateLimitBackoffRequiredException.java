package io.datapulse.etl.v1.execution;

public class LocalRateLimitBackoffRequiredException extends RuntimeException {

  private final long retryAfterMillis;

  public LocalRateLimitBackoffRequiredException(String message, long retryAfterMillis) {
    super(message);
    this.retryAfterMillis = retryAfterMillis;
  }

  public long getRetryAfterMillis() {
    return retryAfterMillis;
  }
}
