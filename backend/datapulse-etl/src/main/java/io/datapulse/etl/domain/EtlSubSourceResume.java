package io.datapulse.etl.domain;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses per-sub-source resume tokens from {@link IngestContext#resumeSubSourceCursor} for Ozon
 * adapters (plain number, JSON map, or opaque {@code last_id} string).
 */
@Slf4j
public final class EtlSubSourceResume {

  private EtlSubSourceResume() {}

  public static long nonNegativeLong(IngestContext ctx, EtlEventType event, String sourceId) {
    String raw = ctx.resumeSubSourceCursor(event, sourceId);
    if (raw == null || raw.isBlank()) {
      return 0L;
    }
    try {
      return Math.max(0L, Long.parseLong(raw.trim()));
    } catch (NumberFormatException e) {
      log.warn("Ignoring invalid numeric resume cursor: sourceId={}, raw={}", sourceId, raw);
      return 0L;
    }
  }

  /**
   * Ozon {@code last_id} cursor: empty string means first page; non-empty is passed through.
   */
  public static String lastIdOrEmpty(IngestContext ctx, EtlEventType event, String sourceId) {
    String raw = ctx.resumeSubSourceCursor(event, sourceId);
    return raw == null ? "" : raw.trim();
  }

  /**
   * Finance API {@code page} (1-based). Invalid or missing → 1.
   */
  public static int ozonFinanceStartPage(IngestContext ctx, EtlEventType event, String sourceId) {
    String raw = ctx.resumeSubSourceCursor(event, sourceId);
    if (raw == null || raw.isBlank()) {
      return 1;
    }
    try {
      return Math.max(1, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      log.warn("Ignoring invalid finance page resume: sourceId={}, raw={}", sourceId, raw);
      return 1;
    }
  }

  /**
   * Product info batch index (0-based), matching {@link io.datapulse.etl.adapter.ozon.OzonProductInfoReadAdapter}
   * loop {@code pageNumber}. Invalid or missing → 0.
   */
  public static int ozonProductInfoStartBatchIndex(
      IngestContext ctx, EtlEventType event, String sourceId) {
    String raw = ctx.resumeSubSourceCursor(event, sourceId);
    if (raw == null || raw.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      log.warn("Ignoring invalid product-info batch resume: sourceId={}, raw={}", sourceId, raw);
      return 0;
    }
  }
}
