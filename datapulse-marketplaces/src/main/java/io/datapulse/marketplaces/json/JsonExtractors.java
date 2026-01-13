package io.datapulse.marketplaces.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class JsonExtractors {

  private JsonExtractors() {
  }

  static String readNullableString(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    return reader.nextString();
  }

  static Long readNullableLong(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    return reader.nextLong();
  }

  static Boolean readNullableBoolean(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    return reader.nextBoolean();
  }

  static String normalizeNullableBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  static String extractString(
      Path jsonFile,
      String failureMessagePrefix,
      StringExtractor extractor
  ) {
    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      return extractor.extract(jsonReader);

    } catch (IOException ex) {
      throw new IllegalStateException(failureMessagePrefix + jsonFile, ex);
    }
  }

  static Boolean extractBoolean(
      Path jsonFile,
      String failureMessagePrefix,
      BooleanExtractor extractor
  ) {
    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8);
        JsonReader jsonReader = new JsonReader(reader)) {

      return extractor.extract(jsonReader);

    } catch (IOException ex) {
      throw new IllegalStateException(failureMessagePrefix + jsonFile, ex);
    }
  }

  @FunctionalInterface
  interface StringExtractor {

    String extract(JsonReader reader) throws IOException;
  }

  @FunctionalInterface
  interface BooleanExtractor {

    Boolean extract(JsonReader reader) throws IOException;
  }
}
