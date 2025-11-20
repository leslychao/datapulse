package io.datapulse.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

  public static <T> String writeJson(T object, ObjectMapper objectMapper)
      throws JsonProcessingException {
    return objectMapper.writeValueAsString(object);
  }

  public static <T> T readJson(String json, Class<T> targetType, ObjectMapper objectMapper)
      throws JsonProcessingException {
    return objectMapper.readValue(json, targetType);
  }
}
