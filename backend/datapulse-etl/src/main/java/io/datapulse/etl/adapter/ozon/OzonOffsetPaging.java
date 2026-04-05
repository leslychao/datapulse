package io.datapulse.etl.adapter.ozon;

/**
 * Ozon-specific constants for offset-based pagination thresholds.
 * Core pagination logic (SHA-256 dedup, max page cap, small-page stop) is in
 * {@link io.datapulse.etl.adapter.util.OffsetPagedCapture}.
 */
public final class OzonOffsetPaging {

  /**
   * Ozon posting list responses below this size are treated as last page.
   */
  public static final int SMALL_PAGE_THRESHOLD_BYTES = 200;

  private OzonOffsetPaging() {}
}
