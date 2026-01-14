package io.datapulse.domain.response.account;

import java.time.OffsetDateTime;

public record AccountResponse(
    Long id,
    String name,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
