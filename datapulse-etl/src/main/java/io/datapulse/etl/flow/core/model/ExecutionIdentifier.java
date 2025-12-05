package io.datapulse.etl.flow.core.model;

import java.util.Objects;

public record ExecutionIdentifier(String requestId, String sourceId) {
  public ExecutionIdentifier {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(sourceId, "sourceId");
  }
}
