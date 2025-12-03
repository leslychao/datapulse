package io.datapulse.marketplaces.dto;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.http.HttpMethod;

public record Snapshot<R>(
    Class<R> elementType,
    Path file,
    long sizeBytes,
    URI sourceUri,
    HttpMethod httpMethod,
    boolean empty
) {

  public static <R> Snapshot<R> of(
      Class<R> elementType,
      Path file,
      long sizeBytes,
      URI sourceUri,
      HttpMethod httpMethod
  ) {
    return new Snapshot<>(elementType, file, sizeBytes, sourceUri, httpMethod, false);
  }

  public static <R> Snapshot<R> empty(Class<R> elementType) {
    return new Snapshot<>(elementType, null, 0, null, null, true);
  }

}
