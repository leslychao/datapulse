package io.datapulse.domain.dto.response;

public record AccountResponse(
    Long id,
    String name,
    String createdAt,
    String updatedAt) {

}
