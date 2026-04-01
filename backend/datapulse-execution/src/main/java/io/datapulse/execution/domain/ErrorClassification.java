package io.datapulse.execution.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorClassification {

  RETRIABLE_RATE_LIMIT("RATE_LIMIT"),
  RETRIABLE_TRANSIENT("TRANSIENT"),
  UNCERTAIN_TIMEOUT("TIMEOUT"),
  NON_RETRIABLE("NON_RECOVERABLE"),
  PROVIDER_ERROR("PROVIDER_ERROR");

  private final String apiValue;

  ErrorClassification(String apiValue) {
    this.apiValue = apiValue;
  }

  @JsonValue
  public String getApiValue() {
    return apiValue;
  }

  @JsonCreator
  public static ErrorClassification fromApi(String value) {
    for (ErrorClassification e : values()) {
      if (e.apiValue.equalsIgnoreCase(value)) {
        return e;
      }
    }
    throw new IllegalArgumentException("Unknown ErrorClassification: " + value);
  }
}
