package io.datapulse.etl.v1.execution;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import io.datapulse.etl.v1.dto.EtlSourceExecution;
import io.datapulse.etl.v1.dto.RunTask;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EtlExecutionPayloadCodec {

  private final Gson gson = new GsonBuilder()
      .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
      .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) -> LocalDate.parse(json.getAsString()))
      .create();

  public Optional<RunTask> parseRunTask(byte[] payload) {
    try {
      return Optional.ofNullable(gson.fromJson(new String(payload, StandardCharsets.UTF_8), RunTask.class));
    } catch (JsonSyntaxException ex) {
      return Optional.empty();
    }
  }

  public Optional<EtlSourceExecution> parseExecution(byte[] payload) {
    try {
      return Optional.ofNullable(gson.fromJson(new String(payload, StandardCharsets.UTF_8), EtlSourceExecution.class));
    } catch (JsonSyntaxException ex) {
      return Optional.empty();
    }
  }

  public String toJson(EtlSourceExecution execution) {
    return gson.toJson(execution);
  }
}
