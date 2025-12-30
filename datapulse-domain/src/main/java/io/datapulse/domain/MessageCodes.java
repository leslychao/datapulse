package io.datapulse.domain;

public final class MessageCodes {

  private MessageCodes() {
  }

  // ===== Common validation =====
  public static final String SERIALIZATION_ERROR = "serialization.error";
  public static final String DTO_REQUIRED = "dto.required";
  public static final String ENTITY_REQUIRED = "entity.required";
  public static final String REQUEST_REQUIRED = "request.required";
  public static final String LIST_REQUIRED = "list.required";
  public static final String PAGEABLE_REQUIRED = "pageable.required";
  public static final String ID_REQUIRED = "id.required";
  public static final String NOT_FOUND = "not-found";
  public static final String DATA_CORRUPTED_ACCOUNT_MISSING = "data.corrupted.account.missing";
  public static final String ERROR_UNKNOWN = "error.unknown";
  public static final String ERROR_REASON = "error.reason";

  // ===== Account =====
  public static final String ACCOUNT_ID_REQUIRED = "account.id.required";
  public static final String ACCOUNT_NAME_REQUIRED = "account.name.required";
  public static final String ACCOUNT_NAME_MAX_LENGTH = "account.name.max-length";
  public static final String ACCOUNT_ALREADY_EXISTS = "account.already-exists";
  public static final String ACCOUNT_NOT_FOUND = "account.not-found";

  // ===== Account-Connection =====
  public static final String ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED = "account.connection.marketplace.required";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED = "account.connection.credentials.required";
  public static final String ACCOUNT_CONNECTION_ACCOUNT_IMMUTABLE = "account.connection.account.immutable";
  public static final String ACCOUNT_CONNECTION_BY_ID_NOT_FOUND = "account.connection.by-id.not-found";
  public static final String ACCOUNT_CONNECTION_BY_ACCOUNT_MARKETPLACE_NOT_FOUND = "account.connection.by-account-marketplace.not-found";
  public static final String ACCOUNT_CONNECTION_INVALID_JSON = "account.connection.credentials.invalid-json";
  public static final String ACCOUNT_CONNECTION_ALREADY_EXISTS = "account.connection.already-exists";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_TYPE_MISMATCH = "account.connection.credentials.type.mismatch";

  // ===== Credentials =====
  public static final String CREDENTIALS_WB_TOKEN_NOT_BLANK = "credentials.wb.token.not-blank";
  public static final String CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK = "credentials.ozon.client-id.not-blank";
  public static final String CREDENTIALS_OZON_API_KEY_NOT_BLANK = "credentials.ozon.api-key.not-blank";

  // ===== Marketplace / HTTP =====
  public static final String UNKNOWN_MARKETPLACE = "unknown-marketplace";
  public static final String WB_MISSING_TOKEN = "wb.missing-token";
  public static final String OZON_MISSING_CREDENTIALS = "ozon.missing-credentials";

  // ===== Marketplace / Config =====
  public static final String MARKETPLACE_CONFIG_MISSING = "marketplace.config.missing";
  public static final String MARKETPLACE_BASE_URL_MISSING = "marketplace.base-url.missing";
  public static final String MARKETPLACE_ENDPOINT_AUTH_SCOPE_REQUIRED = "marketplace.endpoint.auth-scope.required";

  // ===== Marketplace / Config binding (Bean Validation) =====
  public static final String MARKETPLACE_PROVIDERS_REQUIRED = "marketplace.providers.required";
  public static final String MARKETPLACE_ENDPOINTS_REQUIRED = "marketplace.endpoints.required";
  public static final String MARKETPLACE_ENDPOINT_PATH_REQUIRED = "marketplace.endpoint.path.required";

  public static final String MARKETPLACE_RETRY_POLICY_REQUIRED = "marketplace.retry-policy.required";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_ATTEMPTS_REQUIRED = "marketplace.retry-policy.maxAttempts.required";
  public static final String MARKETPLACE_RETRY_POLICY_BASE_BACKOFF_REQUIRED = "marketplace.retry-policy.baseBackoff.required";
  public static final String MARKETPLACE_RETRY_POLICY_MAX_BACKOFF_REQUIRED = "marketplace.retry-policy.maxBackoff.required";

  public static final String MARKETPLACE_RATE_LIMIT_LIMIT_REQUIRED = "marketplace.rate-limit.limit.required";
  public static final String MARKETPLACE_RATE_LIMIT_LIMIT_MIN = "marketplace.rate-limit.limit.min";
  public static final String MARKETPLACE_RATE_LIMIT_PERIOD_REQUIRED = "marketplace.rate-limit.period.required";
  public static final String MARKETPLACE_RATE_LIMIT_PERIOD_MIN = "marketplace.rate-limit.period.min";

  public static final String MARKETPLACE_STORAGE_BASEDIR_REQUIRED = "marketplace.storage.base-dir.required";
  public static final String MARKETPLACE_STORAGE_CLEANUP_MAX_AGE_REQUIRED = "marketplace.storage.cleanup.max-age.required";
  public static final String MARKETPLACE_STORAGE_CLEANUP_MAX_AGE_MIN = "marketplace.storage.cleanup.max-age.min";
  public static final String MARKETPLACE_STORAGE_CLEANUP_INTERVAL_REQUIRED = "marketplace.storage.cleanup.interval.required";
  public static final String MARKETPLACE_STORAGE_CLEANUP_INTERVAL_MIN = "marketplace.storage.cleanup.interval.min";

  // ===== Conversion =====
  public static final String CONVERSION_MAPPING_NOT_FOUND = "conversion.mapping.not.found";

  // ===== File download / streaming =====
  public static final String DOWNLOAD_FAILED = "download.failed";
  public static final String DOWNLOAD_DIR_CREATE_FAILED = "download.dir.create.failed";
  public static final String DOWNLOAD_TMP_CREATE_FAILED = "download.tmp.create.failed";
  public static final String DOWNLOAD_MOVE_FAILED = "download.move.failed";

  // ===== ETL / Events =====
  public static final String ETL_DATE_RANGE_INVALID = "etl.date-range.invalid";
  public static final String ETL_EVENT_REQUIRED = "etl.event.required";
  public static final String ETL_EVENT_UNKNOWN = "etl.event.unknown";
  public static final String ETL_EVENT_SOURCES_MISSING = "etl.event.sources.missing";
  public static final String ETL_REQUEST_INVALID = "etl.request.invalid";
  public static final String ETL_EVENT_DEPENDENCY_NOT_SATISFIED = "etl.event.dependency.not-satisfied";

  // ===== ETL / Ingest / Snapshot =====
  public static final String ETL_INGEST_SNAPSHOT_REQUIRED = "etl.ingest.snapshot.required";
  public static final String ETL_INGEST_SNAPSHOT_FILE_REQUIRED = "etl.ingest.snapshot.file.required";
  public static final String ETL_INGEST_SNAPSHOT_ELEMENT_TYPE_REQUIRED = "etl.ingest.snapshot.element-type.required";
  public static final String ETL_INGEST_JSON_LAYOUT_NOT_FOUND = "etl.ingest.json-layout.not-found";
  public static final String ETL_AGGREGATION_EMPTY_GROUP = "etl.aggregation.empty-group";

  // ===== ETL / RAW reuse =====
  public static final String ETL_RAW_SYNC_REUSE_ENTRY_NOT_FOUND =
      "etl.raw-sync.reuse.entry.not-found";

  // ===== RAW / Schema =====
  public static final String RAW_TABLE_UNSUPPORTED = "raw.table.unsupported";
  public static final String RAW_TABLE_INIT_FAILED = "raw.table.init.failed";

  // ===== Product / Product cost =====
  public static final String PRODUCT_ID_REQUIRED = "product.id.required";
  public static final String PRODUCT_COST_VALUE_REQUIRED = "product.cost.value.required";
  public static final String PRODUCT_COST_VALID_FROM_REQUIRED = "product.cost.valid-from.required";
  public static final String PRODUCT_COST_ACCOUNT_IMMUTABLE = "product.cost.account.immutable";
  public static final String PRODUCT_COST_PRODUCT_IMMUTABLE = "product.cost.product.immutable";

  public static final String PRODUCT_COST_EXCEL_READ_FAILED = "product.cost.excel.read.failed";
  public static final String PRODUCT_COST_PRODUCT_BY_SOURCE_ID_NOT_FOUND = "product-cost.product-by-source-id.not-found";
  public static final String PRODUCT_COST_EXCEL_FIELD_EMPTY = "product.cost.excel.field.empty";
  public static final String PRODUCT_COST_EXCEL_FIELD_NOT_INTEGER = "product.cost.excel.field.not-integer";
  public static final String PRODUCT_COST_EXCEL_FIELD_NOT_NUMBER = "product.cost.excel.field.not-number";
  public static final String PRODUCT_COST_EXCEL_FIELD_NOT_DATE = "product.cost.excel.field.not-date";
}
