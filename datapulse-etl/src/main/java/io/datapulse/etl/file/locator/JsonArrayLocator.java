package io.datapulse.etl.file.locator;

import com.google.gson.stream.JsonReader;

@FunctionalInterface
public interface JsonArrayLocator {

  void moveToArray(JsonReader reader);
}
