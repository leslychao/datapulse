package io.datapulse.etl.adapter.util;

/**
 * Thrown when an API response body is empty (0 bytes written to temp file).
 * Signals graceful end-of-data for cursor-based pagination adapters.
 */
public class EmptyResponseException extends RuntimeException {

  public EmptyResponseException(String message) {
    super(message);
  }
}
