package io.datapulse.etl.file.locator;

public final class JsonArrayLocators {

  private JsonArrayLocators() {
  }

  public static JsonArrayLocator rootArray() {
    return reader -> {
      try {
        reader.beginArray();
      } catch (Exception ex) {
        throw new IllegalStateException("Expected JSON array at root", ex);
      }
    };
  }

  public static JsonArrayLocator resultDataArray() {
    return reader -> {
      try {
        reader.beginObject();
        while (reader.hasNext()) {
          if (reader.nextName().equals("result")) {
            reader.beginObject();
            while (reader.hasNext()) {
              if ("data".equals(reader.nextName())) {
                reader.beginArray();
                return;
              }
              reader.skipValue();
            }
            throw new IllegalStateException("Field 'data' not found inside 'result'");
          } else {
            reader.skipValue();
          }
        }
        throw new IllegalStateException("Field 'result' not found at root");
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to position JSON reader on result.data[]", ex);
      }
    };
  }

  public static JsonArrayLocator itemsArray() {
    return reader -> {
      try {
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          if ("items".equals(name)) {
            reader.beginArray();
            return;
          } else {
            reader.skipValue();
          }
        }
        throw new IllegalStateException("Field 'items' not found at root");
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to position JSON reader on items[]", ex);
      }
    };
  }
}
