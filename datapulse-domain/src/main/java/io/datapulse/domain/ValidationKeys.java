package io.datapulse.domain;

public final class ValidationKeys {

  private ValidationKeys() {
  }

  // ===== Common =====
  public static final String ACCOUNT_ID_REQUIRED =
      "{" + MessageCodes.ACCOUNT_ID_REQUIRED + "}";
  public static final String ACCOUNT_NAME_REQUIRED =
      "{" + MessageCodes.ACCOUNT_NAME_REQUIRED + "}";
  public static final String ACCOUNT_NAME_MAX_LENGTH =
      "{" + MessageCodes.ACCOUNT_NAME_MAX_LENGTH + "}";

  // ===== Account-Connection =====
  public static final String ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED =
      "{" + MessageCodes.ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED + "}";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED =
      "{" + MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED + "}";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH =
      "{" + MessageCodes.ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH + "}";

  // ===== Credentials =====
  public static final String CREDENTIALS_WB_TOKEN_NOT_BLANK =
      "{" + MessageCodes.CREDENTIALS_WB_TOKEN_NOT_BLANK + "}";
  public static final String CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK =
      "{" + MessageCodes.CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK + "}";
  public static final String CREDENTIALS_OZON_API_KEY_NOT_BLANK =
      "{" + MessageCodes.CREDENTIALS_OZON_API_KEY_NOT_BLANK + "}";

  // ===== Marketplace config =====
  public static final String MARKETPLACE_BASE_URL_MISSING =
      "{" + MessageCodes.MARKETPLACE_BASE_URL_MISSING + "}";
  public static final String MARKETPLACE_ENDPOINTS_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_ENDPOINTS_REQUIRED + "}";
  public static final String MARKETPLACE_PROVIDERS_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_PROVIDERS_REQUIRED + "}";
  public static final String MARKETPLACE_STORAGE_BASEDIR_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_STORAGE_BASEDIR_REQUIRED + "}";
  public static final String MARKETPLACE_STORAGE_CLEANUP_MAX_AGE_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_STORAGE_CLEANUP_MAX_AGE_REQUIRED + "}";
  public static final String MARKETPLACE_STORAGE_CLEANUP_MAX_AGE_MIN =
      "{" + MessageCodes.MARKETPLACE_STORAGE_CLEANUP_MAX_AGE_MIN + "}";
  public static final String MARKETPLACE_STORAGE_CLEANUP_INTERVAL_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_STORAGE_CLEANUP_INTERVAL_REQUIRED + "}";
  public static final String MARKETPLACE_STORAGE_CLEANUP_INTERVAL_MIN =
      "{" + MessageCodes.MARKETPLACE_STORAGE_CLEANUP_INTERVAL_MIN + "}";

  // ===== Retry policy =====
  public static final String MARKETPLACE_RETRY_POLICY_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED + "}";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED + "}";

  // ===== Rate limit =====
  public static final String MARKETPLACE_RATE_LIMIT_LIMIT_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RATE_LIMIT_LIMIT_REQUIRED + "}";
  public static final String MARKETPLACE_RATE_LIMIT_LIMIT_MIN =
      "{" + MessageCodes.MARKETPLACE_RATE_LIMIT_LIMIT_MIN + "}";
  public static final String MARKETPLACE_RATE_LIMIT_PERIOD_REQUIRED =
      "{" + MessageCodes.MARKETPLACE_RATE_LIMIT_PERIOD_REQUIRED + "}";
  public static final String MARKETPLACE_RATE_LIMIT_PERIOD_MIN =
      "{" + MessageCodes.MARKETPLACE_RATE_LIMIT_PERIOD_MIN + "}";

  // ===== ETL / EventSource =====
  public static final String ETL_EVENT_REQUIRED =
      "{" + MessageCodes.ETL_EVENT_REQUIRED + "}";
  public static final String ETL_DATE_FROM_REQUIRED =
      "{" + MessageCodes.ETL_DATE_FROM_REQUIRED + "}";
  public static final String ETL_DATE_TO_REQUIRED =
      "{" + MessageCodes.ETL_DATE_TO_REQUIRED + "}";
  public static final String ETL_EVENT_SOURCES_MISSING =
      "{" + MessageCodes.ETL_EVENT_SOURCES_MISSING + "}";
}
