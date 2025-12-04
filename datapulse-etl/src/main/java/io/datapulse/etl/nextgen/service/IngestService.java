package io.datapulse.etl.nextgen.service;

import io.datapulse.etl.nextgen.dto.ExecutionCommand;
import io.datapulse.etl.nextgen.dto.RawIngestPayload;
import io.datapulse.etl.nextgen.support.StreamingJsonReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IngestService {

  private final StreamingJsonReader streamingJsonReader;

  public IngestService(StreamingJsonReader streamingJsonReader) {
    this.streamingJsonReader = streamingJsonReader;
  }

  public RawIngestPayload ingest(ExecutionCommand command, String snapshotJson) {
    List<String> rows = streamingJsonReader.readArray(new ByteArrayInputStream(snapshotJson.getBytes(StandardCharsets.UTF_8)));
    long rawRowsCount = rows.size();
    UUID executionId = command.executionId();
    return new RawIngestPayload(executionId, command.eventId(), command.sourceName(), rawRowsCount, snapshotJson.getBytes(StandardCharsets.UTF_8));
  }
}
