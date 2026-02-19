package io.datapulse.etl.v1.file.locator;

import com.google.gson.stream.JsonReader;

@FunctionalInterface
public interface JsonArrayLocator {

  void moveToArray(JsonReader reader);
}
