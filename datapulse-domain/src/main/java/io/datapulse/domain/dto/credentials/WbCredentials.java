package io.datapulse.domain.dto.credentials;

import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.NotBlank;

public record WbCredentials(
    @NotBlank(message = ValidationKeys.CREDENTIALS_WB_TOKEN_NOT_BLANK)
    String token
) implements MarketplaceCredentials { }
