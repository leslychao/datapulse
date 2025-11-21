package io.datapulse.etl.flow.dto;

public record IngestResult(
    String sourceId,
    boolean success,
    String errorClass,
    String errorMessage
) {

}
