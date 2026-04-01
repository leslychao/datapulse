package io.datapulse.execution.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AttemptOutcome {

  SUCCESS("SUCCESS"),
  RETRIABLE_FAILURE("RETRY"),
  NON_RETRIABLE_FAILURE("FAILURE"),
  UNCERTAIN("INDETERMINATE");

  private final String apiValue;

  AttemptOutcome(String apiValue) {
    this.apiValue = apiValue;
  }

  @JsonValue
  public String getApiValue() {
    return apiValue;
  }

  @JsonCreator
  public static AttemptOutcome fromApi(String value) {
    for (AttemptOutcome o : values()) {
      if (o.apiValue.equalsIgnoreCase(value)) {
        return o;
      }
    }
    throw new IllegalArgumentException("Unknown AttemptOutcome: " + value);
  }
}
