package io.datapulse.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;

public class JsonUtils {

  public static <T> String writeJson(T object, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException ex) {
      throw new AppException(ex, MessageCodes.CREDENTIALS_JSON_SERIALIZATION_ERROR);
    }
  }
}
