package io.datapulse.domain.response;

public record AccountMemberResponse(
    Long id,
    Long accountId,
    Long userId,
    String role,
    String status,
    String createdAt,
    String updatedAt
) {

}
