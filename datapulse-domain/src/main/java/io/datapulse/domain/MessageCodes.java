package io.datapulse.domain;

public final class MessageCodes {

  private MessageCodes() {
  }

  public static final String ACCOUNT_CREATE_REQUEST_REQUIRED = "account.create-request.required";
  public static final String ACCOUNT_UPDATE_REQUEST_REQUIRED = "account.update-request.required";
  public static final String ACCOUNT_ID_REQUIRED = "account.id.required";
  public static final String ACCOUNT_NAME_REQUIRED = "account.name.required";
  public static final String ACCOUNT_NAME_MAX_LENGTH = "account.name.max-length";

  public static final String ACCOUNT_ALREADY_EXISTS = "account.already-exists";
  public static final String ACCOUNT_NOT_FOUND = "account.not-found";

  public static final String ACCOUNT_CONNECTION_MARKETPLACE_REQUIRED = "account.connection.marketplace.required";
  public static final String ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED = "account.connection.credentials.required";
  public static final String ACCOUNT_CONNECTION_ID_IMMUTABLE = "account.connection.id.immutable";
  public static final String ACCOUNT_CONNECTION_NOT_FOUND = "account.connection.not-found";
  public static final String ACCOUNT_CONNECTION_INVALID_JSON = "account.connection.credentials.invalid-json";
  public static final String ACCOUNT_CONNECTION_ALREADY_EXISTS = "account.connection.already-exists";
  public static final String ACCOUNT_CONNECTION_CREATE_REQUEST_REQUIRED = "account.connection.create-request.required";
  public static final String ACCOUNT_CONNECTION_UPDATE_REQUEST_REQUIRED = "account.connection.update-request.required";

  public static final String CREDENTIALS_WB_TOKEN_NOT_BLANK = "credentials.wb.token.not-blank";
  public static final String CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK = "credentials.ozon.client-id.not-blank";
  public static final String CREDENTIALS_OZON_API_KEY_NOT_BLANK = "credentials.ozon.api-key.not-blank";
  public static final String CREDENTIALS_SERIALIZATION_ERROR = "credentials.serialization.error";
  public static final String CREDENTIALS_DESERIALIZATION_ERROR = "credentials.deserialization.error";
  public static final String CREDENTIALS_JSON_SERIALIZATION_ERROR = "credentials.json-serialization.error";

  public static final String CRYPTO_MASTER_KEY_MISSING = "crypto.master-key.missing";
  public static final String CRYPTO_MASTER_KEY_INVALID_BASE64 = "crypto.master-key.invalid-base64";
  public static final String CRYPTO_MASTER_KEY_INVALID_LENGTH = "crypto.master-key.invalid-length";
  public static final String CRYPTO_ENCRYPTION_ERROR = "crypto.encryption.error";
  public static final String CRYPTO_DECRYPTION_ERROR = "crypto.decryption.error";
  public static final String CRYPTO_DECRYPTION_INVALID_FORMAT = "crypto.decryption.invalid-format";
  public static final String CRYPTO_DECRYPTION_UNSUPPORTED_VERSION = "crypto.decryption.unsupported-version";
  public static final String CRYPTO_DECRYPTION_INVALID_IV_LENGTH = "crypto.decryption.invalid-iv-length";

  public static final String ID_REQUIRED = "id.required";
  public static final String NOT_FOUND = "not-found";
  public static final String PARAMS_MUST_BE_KEY_VALUE_PAIRS = "params.must-be-key-value-pairs";
  public static final String URI_REQUIRED = "uri.required";
  public static final String TYPE_REQUIRED = "type.required";
  public static final String REQUEST_INVALID = "request.invalid";
  public static final String UNKNOWN_MARKETPLACE = "unknown-marketplace";
  public static final String WB_MISSING_TOKEN = "wb.missing-token";
  public static final String OZON_MISSING_CREDENTIALS = "ozon.missing-credentials";

  public static final String MARKETPLACE_CONFIG_MISSING = "marketplace.config.missing";
  public static final String MARKETPLACE_BASE_URL_MISSING = "marketplace.base-url.missing";
  public static final String MARKETPLACE_FETCH_FAILED = "marketplace.fetch.failed";
  public static final String MARKETPLACE_PARSE_FAILED = "marketplace.parse.failed";

  public static final String JSON_BODY_INVALID = "json.parse.body.invalid";
}
