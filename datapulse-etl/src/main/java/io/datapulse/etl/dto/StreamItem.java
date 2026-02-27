package io.datapulse.etl.dto;

import java.util.Objects;


public record StreamItem<T>(T payload, boolean last) {

  public StreamItem {
    Objects.requireNonNull(payload, "payload must not be null");
  }

  public static <T> StreamItem<T> of(T payload, boolean last) {
    return new StreamItem<>(payload, last);
  }
}
