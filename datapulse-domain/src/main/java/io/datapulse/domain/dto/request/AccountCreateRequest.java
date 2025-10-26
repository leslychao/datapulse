package io.datapulse.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AccountCreateRequest(
    @NotBlank(message = "{validation.account.name.notBlank}")
    String name
) {}
