package io.datapulse.domain;

public final class ValidationKeys {

  private ValidationKeys() {
  }

  public static final String ACCOUNT_ID_REQUIRED = "{" + MessageCodes.ACCOUNT_ID_REQUIRED + "}";
  public static final String ACCOUNT_NAME_REQUIRED = "{" + MessageCodes.ACCOUNT_NAME_REQUIRED + "}";
  public static final String ACCOUNT_NAME_MAX_LENGTH =
      "{" + MessageCodes.ACCOUNT_NAME_MAX_LENGTH + "}";

  public static final String ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED =
      "{" + MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED + "}";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED =
      "{" + MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED + "}";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH =
      "{" + MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH + "}";

  public static final String CREDENTIALS_WB_TOKEN_NOT_BLANK =
      "{" + MessageCodes.CREDENTIALS_WB_TOKEN_NOT_BLANK + "}";
  public static final String CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK =
      "{" + MessageCodes.CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK + "}";
  public static final String CREDENTIALS_OZON_API_KEY_NOT_BLANK =
      "{" + MessageCodes.CREDENTIALS_OZON_API_KEY_NOT_BLANK + "}";

  // Marketplace / Provider
  public static final String MARKETPLACE_BASE_URL_MISSING =
      "{" + MessageCodes.MARKETPLACE_BASE_URL_MISSING + "}";

  public static final String MARKETPLACE_ENDPOINTS_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_ENDPOINTS_REQUIRED + "}";

  public static final String MARKETPLACE_ENDPOINT_PATH_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_ENDPOINT_PATH_REQUIRED + "}";

  public static final String MARKETPLACE_RETRY_POLICY_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_REQUIRED + "}";

  public static final String MARKETPLACE_RETRY_POLICY_LIMIT_FOR_PERIOD_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_LIMIT_FOR_PERIOD_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_CONCURRENT_CALLS_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_MAX_CONCURRENT_CALLS_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_JITTER_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_MAX_JITTER_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_RETRY_AFTER_FALLBACK_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_RETRY_AFTER_FALLBACK_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_LIMIT_REFRESH_PERIOD_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_LIMIT_REFRESH_PERIOD_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_TOKEN_WAIT_TIMEOUT_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_TOKEN_WAIT_TIMEOUT_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_BULKHEAD_WAIT_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_BULKHEAD_WAIT_REQUIRED + "}";
}
