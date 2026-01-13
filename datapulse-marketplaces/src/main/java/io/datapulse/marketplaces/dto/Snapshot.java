package io.datapulse.marketplaces.dto;

import java.nio.file.Path;

public record Snapshot<R>(
    Class<R> elementType,
    Path file,
    String nextToken
) {

  public Snapshot(Class<R> elementType, Path file) {
    this(elementType, file, null);
  }
}
