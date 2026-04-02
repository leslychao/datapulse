package io.datapulse.analytics.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.AppException;

public class AnalyticsUnavailableException extends AppException {

  private static final int HTTP_SERVICE_UNAVAILABLE = 503;
  private static final int RETRY_AFTER_SECONDS = 30;

  public AnalyticsUnavailableException() {
    super(MessageCodes.ANALYTICS_CLICKHOUSE_UNAVAILABLE, HTTP_SERVICE_UNAVAILABLE,
        RETRY_AFTER_SECONDS);
  }

  public AnalyticsUnavailableException(Throwable cause) {
    super(MessageCodes.ANALYTICS_CLICKHOUSE_UNAVAILABLE, HTTP_SERVICE_UNAVAILABLE, cause,
        RETRY_AFTER_SECONDS);
  }

  public int retryAfterSeconds() {
    return RETRY_AFTER_SECONDS;
  }
}
