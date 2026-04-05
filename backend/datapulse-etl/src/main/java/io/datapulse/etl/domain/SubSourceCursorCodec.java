package io.datapulse.etl.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Formats {@code last_cursor} for DLX retry when an {@link EtlEventType} has multiple sub-sources.
 *
 * <p>Single sub-source with a cursor stores a plain token (e.g. {@code "5000"}). Multiple sub-sources
 * with cursors use JSON {@code {"o":{"SourceIdA":"100","SourceIdB":"200"}}} so each adapter can
 * resume independently.</p>
 */
public final class SubSourceCursorCodec {

  private static final String SUB_SOURCES_KEY = "o";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private SubSourceCursorCodec() {}

  /**
   * Returns the resume token for {@code sourceId} from a checkpoint {@code last_cursor} value.
   *
   * <p>If {@code raw} is JSON with map {@code o}, returns {@code o[sourceId]}. Otherwise returns
   * {@code raw} trimmed (legacy: one cursor shared for the whole event).</p>
   */
  public static String resolve(String raw, String sourceId) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    if (!trimmed.startsWith("{")) {
      return trimmed;
    }
    try {
      JsonNode root = MAPPER.readTree(trimmed);
      JsonNode map = root.get(SUB_SOURCES_KEY);
      if (map == null || !map.has(sourceId) || map.get(sourceId).isNull()) {
        return null;
      }
      String value = map.get(sourceId).asText();
      return value == null || value.isBlank() ? null : value;
    } catch (JsonProcessingException e) {
      return trimmed;
    }
  }

  /**
   * Builds {@link EventResult#lastCursor()} from sub-source results that carry a resume token.
   */
  public static String mergeSubSourceLastCursors(List<SubSourceResult> subSources) {
    Map<String, String> bySource = new LinkedHashMap<>();
    for (SubSourceResult s : subSources) {
      if (s.lastCursor() == null || s.lastCursor().isBlank()) {
        continue;
      }
      bySource.put(s.sourceId(), s.lastCursor().trim());
    }
    if (bySource.isEmpty()) {
      return null;
    }
    if (bySource.size() == 1) {
      return bySource.values().iterator().next();
    }
    try {
      return MAPPER.writeValueAsString(Map.of(SUB_SOURCES_KEY, bySource));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize sub-source cursors", e);
    }
  }
}
