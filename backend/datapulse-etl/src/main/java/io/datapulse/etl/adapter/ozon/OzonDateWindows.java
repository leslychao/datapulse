package io.datapulse.etl.adapter.ozon;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a date range into fixed-size windows to respect Ozon API limits
 * (FBS orders: ~3 months, finance: 1 month).
 */
public final class OzonDateWindows {

  private OzonDateWindows() {}

  public record Window(OffsetDateTime since, OffsetDateTime to) {}

  public static List<Window> split(OffsetDateTime since, OffsetDateTime to, int maxDays) {
    if (!since.isBefore(to)) {
      return List.of(new Window(since, to));
    }
    List<Window> windows = new ArrayList<>();
    OffsetDateTime current = since;
    while (current.isBefore(to)) {
      OffsetDateTime windowEnd = current.plusDays(maxDays);
      if (windowEnd.isAfter(to)) {
        windowEnd = to;
      }
      windows.add(new Window(current, windowEnd));
      current = windowEnd;
    }
    return windows;
  }
}
