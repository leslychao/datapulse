package io.datapulse.etl.dto;

public record IngestResult(
    String sourceId,
    boolean success,
    String errorClass,
    String errorMessage
) {

}
