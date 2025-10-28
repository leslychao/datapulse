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
}
