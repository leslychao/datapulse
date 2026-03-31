package io.datapulse.etl.domain;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Serializes / deserializes checkpoint JSON for DLX retry resume.
 * Checkpoint tracks per-event progress so the worker can skip completed events
 * and resume failed ones from their last cursor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointManager {

    private final ObjectMapper objectMapper;

    /**
     * Parses checkpoint JSON from job_execution into a typed map.
     *
     * @return empty map if checkpoint is null or empty
     */
    public Map<EtlEventType, IngestContext.CheckpointEntry> parse(String checkpointJson) {
        if (checkpointJson == null || checkpointJson.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> root = objectMapper.readValue(checkpointJson,
                    new TypeReference<>() {});

            Object eventsObj = root.get("events");
            if (!(eventsObj instanceof Map<?, ?> eventsMap)) {
                return Map.of();
            }

            Map<EtlEventType, IngestContext.CheckpointEntry> result = new EnumMap<>(EtlEventType.class);

            for (Map.Entry<?, ?> entry : eventsMap.entrySet()) {
                try {
                    EtlEventType eventType = EtlEventType.valueOf(entry.getKey().toString());
                    String entryJson = objectMapper.writeValueAsString(entry.getValue());
                    CheckpointEntryDto dto = objectMapper.readValue(entryJson, CheckpointEntryDto.class);

                    result.put(eventType, new IngestContext.CheckpointEntry(
                            parseStatus(dto.status),
                            dto.last_cursor,
                            dto.error_type,
                            dto.error
                    ));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown event type in checkpoint: key={}", entry.getKey());
                }
            }

            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse checkpoint JSON, starting from scratch", e);
            return Map.of();
        }
    }

    /**
     * Builds checkpoint JSON from the current event results + retry metadata.
     */
    public String serialize(Map<EtlEventType, EventResult> results, int retryCount) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> events = new LinkedHashMap<>();

            for (Map.Entry<EtlEventType, EventResult> entry : results.entrySet()) {
                EventResult result = entry.getValue();
                Map<String, Object> eventEntry = new LinkedHashMap<>();
                eventEntry.put("status", result.status().name());

                if (result.lastCursor() != null) {
                    eventEntry.put("last_cursor", result.lastCursor());
                }
                if (result.isFailed() && !result.subSourceResults().isEmpty()) {
                    SubSourceResult lastFailed = result.subSourceResults().stream()
                            .filter(s -> s.status() == EventResultStatus.FAILED)
                            .reduce((first, second) -> second)
                            .orElse(null);
                    if (lastFailed != null && !lastFailed.errors().isEmpty()) {
                        eventEntry.put("error_type", "API_ERROR");
                        eventEntry.put("error", lastFailed.errors().get(0));
                    }
                }
                if (result.isSkipped() && result.skipReason() != null) {
                    eventEntry.put("reason", result.skipReason());
                }

                events.put(entry.getKey().name(), eventEntry);
            }

            root.put("events", events);
            root.put("retry_count", retryCount);
            if (retryCount > 0) {
                root.put("last_retry_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize checkpoint", e);
        }
    }

    /**
     * Extracts the retry_count from checkpoint JSON.
     */
    public int extractRetryCount(String checkpointJson) {
        if (checkpointJson == null || checkpointJson.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(checkpointJson,
                    new TypeReference<>() {});
            Object count = root.get("retry_count");
            if (count instanceof Number n) {
                return n.intValue();
            }
            return 0;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private EventResultStatus parseStatus(String status) {
        if (status == null) {
            return EventResultStatus.FAILED;
        }
        return switch (status) {
            case "COMPLETED" -> EventResultStatus.COMPLETED;
            case "FAILED" -> EventResultStatus.FAILED;
            case "SKIPPED" -> EventResultStatus.SKIPPED;
            default -> EventResultStatus.FAILED;
        };
    }

    private record CheckpointEntryDto(String status, String last_cursor, String error_type, String error) {}
}
