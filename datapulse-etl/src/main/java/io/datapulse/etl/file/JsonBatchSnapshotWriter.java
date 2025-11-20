package io.datapulse.etl.file;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import io.datapulse.domain.exception.AppException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class JsonBatchSnapshotWriter {

  private static final Gson GSON = new GsonBuilder().create();

  private JsonBatchSnapshotWriter() {
  }

  public static <R> Path writeToTempFile(
      Class<R> elementType,
      Supplier<List<R>> batchSupplier
  ) {
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(batchSupplier, "batchSupplier must not be null");

    Path file = createTempFile();
    writeToFile(file, elementType, batchSupplier);
    return file;
  }

  public static <R> void writeToFile(
      Path file,
      Class<R> elementType,
      Supplier<List<R>> batchSupplier
  ) {
    Objects.requireNonNull(file, "file must not be null");
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(batchSupplier, "batchSupplier must not be null");

    try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(
        Files.newOutputStream(file),
        StandardCharsets.UTF_8
    ))) {
      writer.beginArray();

      while (true) {
        List<R> batch = batchSupplier.get();
        if (batch == null || batch.isEmpty()) {
          break;
        }
        for (R element : batch) {
          GSON.toJson(element, elementType, writer);
        }
      }

      writer.endArray();
    } catch (IOException ex) {
      throw new AppException(DOWNLOAD_FAILED, file.toString());
    }
  }

  private static Path createTempFile() {
    try {
      return Files.createTempFile("etl-snapshot-", ".json");
    } catch (IOException ex) {
      throw new AppException(DOWNLOAD_FAILED, "etl-snapshot-*.json");
    }
  }
}
