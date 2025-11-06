package io.datapulse.domain;

public final class MessageCodes {

  private MessageCodes() {
  }

  // ===== Account =====
  public static final String ACCOUNT_CREATE_REQUEST_REQUIRED = "account.create-request.required";
  public static final String ACCOUNT_UPDATE_REQUEST_REQUIRED = "account.update-request.required";
  public static final String ACCOUNT_ID_REQUIRED = "account.id.required";
  public static final String ACCOUNT_NAME_REQUIRED = "account.name.required";
  public static final String ACCOUNT_NAME_MAX_LENGTH = "account.name.max-length";
  public static final String ACCOUNT_ALREADY_EXISTS = "account.already-exists";
  public static final String ACCOUNT_NOT_FOUND = "account.not-found";

  // ===== Account-Connection =====
  public static final String ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED = "account.connection.marketplace.required";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED = "account.connection.credentials.required";
  public static final String ACCOUNT_CONNECTION_ACCOUNT_IMMUTABLE = "account.connection.account.immutable";
  public static final String ACCOUNT_CONNECTION_BY_ID_NOT_FOUND =
      "account.connection.by-id.not-found";
  public static final String ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND =
      "account.connection.by-account-marketplace.not-found";
  public static final String ACCOUNT_CONNECTION_INVALID_JSON = "account.connection.credentials.invalid-json";
  public static final String ACCOUNT_CONNECTION_ALREADY_EXISTS = "account.connection.already-exists";
  public static final String ACCOUNT_CONNECTION_CREATE_REQUEST_REQUIRED = "account.connection.create-request.required";
  public static final String ACCOUNT_CONNECTION_UPDATE_REQUEST_REQUIRED = "account.connection.update-request.required";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH = "account_connection.credentials.type.mismatch";


  // ===== Credentials =====
  public static final String CREDENTIALS_WB_TOKEN_NOT_BLANK = "credentials.wb.token.not-blank";
  public static final String CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK = "credentials.ozon.client-id.not-blank";
  public static final String CREDENTIALS_OZON_API_KEY_NOT_BLANK = "credentials.ozon.api-key.not-blank";
  public static final String CREDENTIALS_SERIALIZATION_ERROR = "credentials.serialization.error";
  public static final String CREDENTIALS_DESERIALIZATION_ERROR = "credentials.deserialization.error";

  // ===== Crypto =====
  public static final String CRYPTO_MASTER_KEY_MISSING = "crypto.master-key.missing";
  public static final String CRYPTO_MASTER_KEY_INVALID_BASE64 = "crypto.master-key.invalid-base64";
  public static final String CRYPTO_MASTER_KEY_INVALID_LENGTH = "crypto.master-key.invalid-length";
  public static final String CRYPTO_ENCRYPTION_ERROR = "crypto.encryption.error";
  public static final String CRYPTO_DECRYPTION_ERROR = "crypto.decryption.error";
  public static final String CRYPTO_DECRYPTION_INVALID_FORMAT = "crypto.decryption.invalid-format";
  public static final String CRYPTO_DECRYPTION_UNSUPPORTED_VERSION = "crypto.decryption.unsupported-version";
  public static final String CRYPTO_DECRYPTION_INVALID_IV_LENGTH = "crypto.decryption.invalid-iv-length";

  // ===== Common validation =====
  public static final String DTO_REQUIRED = "dto.required";
  public static final String ENTITY_REQUIRED = "entity.required";
  public static final String REQUEST_REQUIRED  = "request.required";
  public static final String LIST_REQUIRED = "list.required";
  public static final String PAGEABLE_REQUIRED = "pageable.required";
  public static final String ID_REQUIRED = "id.required";
  public static final String NOT_FOUND = "not-found";
  public static final String JSON_BODY_INVALID = "json.parse. body.invalid";
  public static final String URI_REQUIRED = "uri.required";
  public static final String TYPE_REQUIRED = "type.required";
  public static final String REQUEST_DATE_REQUIRED = "request.date.required";
  public static final String REQUEST_DATE_INVALID = "request.date.invalid";
  public static final String DATA_CORRUPTED_ACCOUNT_MISSING = "data.corrupted.account.missing";
  public static final String DATA_CORRUPTED_CREDENTIALS_MISSING = "data.corrupted.credentials.missing";

  // ===== Marketplace / HTTP =====
  public static final String MARKETPLACE_REQUIRED = "marketplace.required";
  public static final String UNKNOWN_MARKETPLACE = "unknown-marketplace";
  public static final String WB_MISSING_TOKEN = "wb.missing-token";
  public static final String OZON_MISSING_CREDENTIALS = "ozon.missing-credentials";
  public static final String MARKETPLACE_CONFIG_MISSING = "marketplace.config.missing";
  public static final String MARKETPLACE_BASE_URL_MISSING = "marketplace.base-url.missing";
  public static final String MARKETPLACE_FETCH_FAILED = "marketplace.fetch.failed";
  public static final String MARKETPLACE_PARSE_FAILED = "marketplace.parse.failed";
  public static final String MARKETPLACE_EVENT_ENDPOINTS_MISSING = "marketplace.event-endpoints.missing";
  public static final String MARKETPLACE_ENDPOINT_PATH_MISSING = "marketplace.endpoint-path.missing";

  // ===== Marketplace / Config binding (Bean Validation) =====
  public static final String MARKETPLACE_ENDPOINTS_REQUIRED = "marketplace.endpoints.required";
  public static final String MARKETPLACE_ENDPOINT_PATH_REQUIRED = "marketplace.endpoint.path.required";
  public static final String MARKETPLACE_RESILIENCE_REQUIRED = "marketplace.resilience.required";
  public static final String MARKETPLACE_RESILIENCE_LIMIT_FOR_PERIOD_REQUIRED = "marketplace.resilience.limitForPeriod.required";
  public static final String MARKETPLACE_RESILIENCE_MAX_CONCURRENT_CALLS_REQUIRED = "marketplace.resilience.maxConcurrentCalls.required";
  public static final String MARKETPLACE_RESILIENCE_MAX_ATTEMPTS_REQUIRED = "marketplace.resilience.maxAttempts.required";
  public static final String MARKETPLACE_RESILIENCE_BASE_BACKOFF_REQUIRED = "marketplace.resilience.baseBackoff.required";
  public static final String MARKETPLACE_RESILIENCE_MAX_BACKOFF_REQUIRED = "marketplace.resilience.maxBackoff.required";
  public static final String MARKETPLACE_RESILIENCE_MAX_JITTER_REQUIRED = "marketplace.resilience.maxJitter.required";
  public static final String MARKETPLACE_RESILIENCE_RETRY_AFTER_FALLBACK_REQUIRED = "marketplace.resilience.retryAfterFallback.required";
  public static final String MARKETPLACE_RESILIENCE_LIMIT_REFRESH_PERIOD_REQUIRED = "marketplace.resilience.limitRefreshPeriod.required";
  public static final String MARKETPLACE_RESILIENCE_TOKEN_WAIT_TIMEOUT_REQUIRED = "marketplace.resilience.tokenWaitTimeout.required";
  public static final String MARKETPLACE_RESILIENCE_BULKHEAD_WAIT_REQUIRED = "marketplace.resilience.bulkheadWait.required";

  public static final String CONVERSION_MAPPING_NOT_FOUND = "conversion.mapping.not.found";

}
