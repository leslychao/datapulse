package io.datapulse.bidding.domain;

import java.util.Map;

public record ExplanationMessage(
    String key,
    Map<String, Object> args
) {

  public static ExplanationMessage of(String key) {
    return new ExplanationMessage(key, Map.of());
  }

  public static ExplanationMessage of(String key, Map<String, Object> args) {
    return new ExplanationMessage(key, args != null ? args : Map.of());
  }
}
