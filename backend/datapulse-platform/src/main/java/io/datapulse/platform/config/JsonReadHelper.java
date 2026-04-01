package io.datapulse.platform.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonReadHelper {

  private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  @Named("jsonToObject")
  public Object jsonToObject(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse JSONB column value: {}", e.getMessage());
      return null;
    }
  }

  @Named("jsonToStringList")
  public List<String> jsonToStringList(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, STRING_LIST_TYPE);
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse JSONB column as string list: {}", e.getMessage());
      return null;
    }
  }
}
