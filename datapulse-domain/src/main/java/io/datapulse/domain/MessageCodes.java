package io.datapulse.domain;

public final class MessageCodes {

  private MessageCodes() {
  }

  public static final String ACCOUNT_ID_REQUIRED = "account.id.required";
  public static final String ACCOUNT_NAME_REQUIRED = "account.name.required";
  public static final String ACCOUNT_ALREADY_EXISTS = "account.already-exists";
  public static final String ACCOUNT_NOT_FOUND = "account.not-found";

  public static final String ACCOUNT_CONNECTION_ID_IMMUTABLE = "account.connection.id.immutable";
  public static final String ACCOUNT_CONNECTION_NOT_FOUND = "account.connection.not-found";
  public static final String ACCOUNT_CONNECTION_INVALID_JSON = "account.connection.credentials.invalid-json";
  public static final String ACCOUNT_CONNECTION_ALREADY_EXISTS = "account.connection.already-exists";


  public static final String CREDENTIALS_SERIALIZATION_ERROR = "credentials.serialization.error";
  public static final String CREDENTIALS_DESERIALIZATION_ERROR = "credentials.deserialization.error";
  public static final String CREDENTIALS_JSON_SERIALIZATION_ERROR = "credentials.json.serialization.error";

  public static final String CRYPTO_MASTER_KEY_MISSING = "crypto.masterKey.missing";
  public static final String CRYPTO_MASTER_KEY_INVALID_BASE64 = "crypto.masterKey.invalidBase64";
  public static final String CRYPTO_MASTER_KEY_INVALID_LENGTH = "crypto.masterKey.invalidLength";
  public static final String CRYPTO_ENCRYPTION_ERROR = "crypto.encryption.error";
  public static final String CRYPTO_DECRYPTION_ERROR = "crypto.decryption.error";
  public static final String CRYPTO_DECRYPTION_INVALID_FORMAT = "crypto.decryption.invalidFormat";
  public static final String CRYPTO_DECRYPTION_UNSUPPORTED_VERSION = "crypto.decryption.unsupportedVersion";
  public static final String CRYPTO_DECRYPTION_INVALID_IV_LENGTH = "crypto.decryption.invalidIvLength";

  public static final String VALIDATION_PARAMS_MUST_BE_KEY_VALUE_PAIRS = "validation.params.mustBeKeyValuePairs";
  public static final String VALIDATION_URI_REQUIRED = "validation.uri.required";
  public static final String VALIDATION_TYPE_REQUIRED = "validation.type.required";
  public static final String VALIDATION_REQUEST_INVALID = "validation.request.invalid";


  public static final String MARKETPLACE_CONFIG_MISSING = "marketplace.config.missing";
  public static final String MARKETPLACE_BASEURL_MISSING = "marketplace.baseUrl.missing";
  public static final String MARKETPLACE_FETCH_FAILED = "marketplace.fetch.failed";
  public static final String MARKETPLACE_PARSE_FAILED = "marketplace.parse.failed";

  public static final String HTTP_HEADERS_UNKNOWN_MARKETPLACE = "http.headers.unknown-marketplace";
  public static final String HTTP_HEADERS_WB_MISSING_TOKEN = "http.headers.wb.missing-token";
  public static final String HTTP_HEADERS_OZON_MISSING_CREDENTIALS = "http.headers.ozon.missing-credentials";

  public static final String JSON_BODY_INVALID = "json.parse.body.invalid";

  public static final String ID_REQUIRED = "id.required";
  public static final String NOT_FOUND = "not.found";
}
