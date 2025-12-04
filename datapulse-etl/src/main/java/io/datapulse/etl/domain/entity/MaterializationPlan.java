package io.datapulse.etl.domain.entity;

import java.util.UUID;

public record MaterializationPlan(UUID eventId, String target, boolean requiresNormalization) {

  public MaterializationPlan {
    if (eventId == null) {
      throw new IllegalArgumentException("Event id is required for materialization plan");
    }
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("Materialization target is required");
    }
  }
}
