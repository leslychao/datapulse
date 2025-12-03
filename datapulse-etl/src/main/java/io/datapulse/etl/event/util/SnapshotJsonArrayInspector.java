package io.datapulse.etl.event.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SnapshotJsonArrayInspector {

  private final ObjectMapper objectMapper;

  public boolean isArrayEmpty(Path snapshotFile) {
    return isArrayEmpty(snapshotFile, List.of());
  }

  public boolean isArrayEmpty(Path snapshotFile, String field) {
    return isArrayEmpty(snapshotFile, List.of(field));
  }

  public boolean isArrayEmpty(Path snapshotFile, String... fieldPath) {
    return isArrayEmpty(snapshotFile, Arrays.asList(fieldPath));
  }

  private boolean isArrayEmpty(Path snapshotFile, List<String> fieldPath) {
    if (snapshotFile == null) {
      throw new IllegalArgumentException("snapshotFile must not be null");
    }
    try {
      JsonNode node = objectMapper.readTree(snapshotFile.toFile());
      for (String field : fieldPath) {
        node = node.path(field);
      }
      return node.isArray() && node.isEmpty();
    } catch (IOException ex) {
      throw new RuntimeException("Failed to analyze snapshot json", ex);
    }
  }
}
