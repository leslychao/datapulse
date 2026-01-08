package io.datapulse.domain.dto.response;

public record AccountMemberResponse(
    Long id,
    Long accountId,
    Long userId,
    String role,
    String createdAt,
    String updatedAt
) {

}
