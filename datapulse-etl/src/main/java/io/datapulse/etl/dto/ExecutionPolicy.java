package io.datapulse.etl.dto;

public record ExecutionPolicy(
    int maxAttempts
) {
  public static ExecutionPolicy defaultPolicy() {
    return new ExecutionPolicy(5);
  }
}