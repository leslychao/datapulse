package io.datapulse.domain.response.account;

public record AccountResponse(
    Long id,
    String name,
    String createdAt,
    String updatedAt) {

}
