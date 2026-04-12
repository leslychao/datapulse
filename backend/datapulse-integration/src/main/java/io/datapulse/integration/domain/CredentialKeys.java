package io.datapulse.integration.domain;

/**
 * Canonical Vault credential key names.
 * All modules that read credentials from Vault must use these constants
 * to prevent key naming drift across modules.
 *
 * Source of truth: {@link CredentialMapper} writes credentials with these keys.
 */
public final class CredentialKeys {

  private CredentialKeys() {
  }

  public static final String WB_API_TOKEN = "apiToken";

  public static final String OZON_CLIENT_ID = "clientId";
  public static final String OZON_API_KEY = "apiKey";

  public static final String OZON_PERFORMANCE_CLIENT_ID = "performanceClientId";
  public static final String OZON_PERFORMANCE_CLIENT_SECRET = "performanceClientSecret";

  public static final String YANDEX_API_KEY = "apiKey";

  /**
   * Yandex businessId — enriched from connection metadata (not from Vault).
   * Used by {@code ExecutionCredentialResolver} and price adapters.
   */
  public static final String YANDEX_BUSINESS_ID = "businessId";
}
