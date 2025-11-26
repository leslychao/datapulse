package io.datapulse.etl.dto;

public record IngestResult(
    String sourceId,
    IngestStatus status,
    String errorClass,
    String errorMessage
) {

  public boolean isSuccess() {
    return status == IngestStatus.SUCCESS;
  }

  public boolean isWait() {
    return status == IngestStatus.WAIT;
  }

  public boolean isError() {
    return status == IngestStatus.ERROR;
  }
}
