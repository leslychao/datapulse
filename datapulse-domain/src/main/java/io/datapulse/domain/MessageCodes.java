package io.datapulse.domain;

public final class MessageCodes {

  private MessageCodes() {
  }

  // ===== Account =====
  public static final String ACCOUNT_ID_REQUIRED = "account.id.required";
  public static final String ACCOUNT_NAME_REQUIRED = "account.name.required";
  public static final String ACCOUNT_NAME_MAX_LENGTH = "account.name.max-length";
  public static final String ACCOUNT_ALREADY_EXISTS = "account.already-exists";
  public static final String ACCOUNT_NOT_FOUND = "account.not-found";

  // ===== Account-Connection =====
  public static final String ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED =
      "account.connection.marketplace.required";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED =
      "account.connection.credentials.required";
  public static final String ACCOUNT_CONNECTION_ACCOUNT_IMMUTABLE =
      "account.connection.account.immutable";
  public static final String ACCOUNT_CONNECTION_BY_ID_NOT_FOUND =
      "account.connection.by-id.not-found";
  public static final String ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND =
      "account.connection.by-account-marketplace.not-found";
  public static final String ACCOUNT_CONNECTION_INVALID_JSON =
      "account.connection.credentials.invalid-json";
  public static final String ACCOUNT_CONNECTION_ALREADY_EXISTS =
      "account.connection.already-exists";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH =
      "account_connection.credentials.type.mismatch";

  // ===== Credentials =====
  public static final String CREDENTIALS_WB_TOKEN_NOT_BLANK =
      "credentials.wb.token.not-blank";
  public static final String CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK =
      "credentials.ozon.client-id.not-blank";
  public static final String CREDENTIALS_OZON_API_KEY_NOT_BLANK =
      "credentials.ozon.api-key.not-blank";

  // ===== Common validation =====
  public static final String SERIALIZATION_ERROR = "serialization.error";
  public static final String DTO_REQUIRED = "dto.required";
  public static final String ENTITY_REQUIRED = "entity.required";
  public static final String REQUEST_REQUIRED = "request.required";
  public static final String LIST_REQUIRED = "list.required";
  public static final String PAGEABLE_REQUIRED = "pageable.required";
  public static final String ID_REQUIRED = "id.required";
  public static final String NOT_FOUND = "not-found";
  public static final String DATA_CORRUPTED_ACCOUNT_MISSING =
      "data.corrupted.account.missing";
  public static final String ERROR_UNKNOWN = "error.unknown";
  public static final String ERROR_REASON = "error.reason";

  // ===== Marketplace / HTTP =====
  public static final String UNKNOWN_MARKETPLACE = "unknown-marketplace";
  public static final String WB_MISSING_TOKEN = "wb.missing-token";
  public static final String OZON_MISSING_CREDENTIALS = "ozon.missing-credentials";
  public static final String MARKETPLACE_CONFIG_MISSING = "marketplace.config.missing";
  public static final String MARKETPLACE_BASE_URL_MISSING =
      "marketplace.base-url.missing";

  // ===== Marketplace / Config binding (Bean Validation) =====
  public static final String MARKETPLACE_ENDPOINTS_REQUIRED =
      "marketplace.endpoints.required";
  public static final String MARKETPLACE_ENDPOINT_PATH_REQUIRED =
      "marketplace.endpoint.path.required";
  public static final String MARKETPLACE_RETRY_POLICY_REQUIRED =
      "marketplace.retry-policy.required";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED =
      "marketplace.retry-policy.maxAttempts.required";
  public static final String MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED =
      "marketplace.retry-policy.baseBackoff.required";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED =
      "marketplace.retry-policy.maxBackoff.required";
  public static final String MARKETPLACE_RATE_LIMIT_RULES_REQUIRED =
      "marketplace.rate-limit.rules.required";
  public static final String MARKETPLACE_RATE_LIMIT_MAX_PER_MINUTE_INVALID =
      "marketplace.rate-limit.max-per-minute.invalid";
  public static final String MARKETPLACE_RATE_LIMIT_MIN_INTERVAL_REQUIRED =
      "marketplace.rate-limit.min-interval.required";
  public static final String MARKETPLACE_PROVIDERS_REQUIRED =
      "marketplace.providers.required";
  public static final String MARKETPLACE_STORAGE_BASEDIR_REQUIRED =
      "marketplace.storage.base-dir.required";

  // ===== CONVERSION =====
  public static final String CONVERSION_MAPPING_NOT_FOUND =
      "conversion.mapping.not.found";

  // ===== File download / streaming =====
  public static final String DOWNLOAD_FAILED = "download.failed";
  public static final String DOWNLOAD_DIR_CREATE_FAILED =
      "download.dir.create.failed";
  public static final String DOWNLOAD_TMP_CREATE_FAILED =
      "download.tmp.create.failed";
  public static final String DOWNLOAD_MOVE_FAILED = "download.move.failed";

  // ===== ETL / Events =====
  public static final String ETL_DATE_FROM_REQUIRED = "etl.date-from.required";
  public static final String ETL_DATE_TO_REQUIRED = "etl.date-to.required";
  public static final String ETL_CONTEXT_MISSING = "etl.context.missing";
  public static final String ETL_EVENT_REQUIRED = "etl.event.required";
  public static final String ETL_EVENT_UNKNOWN = "etl.event.unknown";
  public static final String ETL_EVENT_SOURCES_MISSING =
      "etl.event.sources.missing";
  public static final String ETL_REQUEST_INVALID = "etl.request.invalid";
  public static final String ETL_MATERIALIZATION_FALLBACK_ERROR =
      "etl.materialization.error.fallback";


  // ===== ETL / Ingest / Snapshot =====
  public static final String ETL_INGEST_SNAPSHOT_REQUIRED =
      "etl.ingest.snapshot.required";
  public static final String ETL_INGEST_SNAPSHOT_FILE_REQUIRED =
      "etl.ingest.snapshot.file.required";
  public static final String ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED =
      "etl.ingest.snapshot.element-type.required";
  public static final String ETL_INGEST_JSON_LAYOUT_NOT_FOUND =
      "etl.ingest.json-layout.not-found";
  public static final String ETL_INGEST_SOURCE_NO_DATA = "etl.ingest.source.no-data";

  // ===== RAW / Schema =====
  public static final String RAW_TABLE_UNSUPPORTED = "raw.table.unsupported";
  public static final String RAW_TABLE_INIT_FAILED = "raw.table.init.failed";
}
