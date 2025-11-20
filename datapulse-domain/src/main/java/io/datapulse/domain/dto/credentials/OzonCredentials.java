package io.datapulse.domain.dto.credentials;

import io.datapulse.domain.ValidationKeys;
import jakarta.validation.constraints.NotBlank;

public record OzonCredentials(
    @NotBlank(message = ValidationKeys.CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK)
    String clientId,
    @NotBlank(message = ValidationKeys.CREDENTIALS_OZON_API_KEY_NOT_BLANK)
    String apiKey
) implements MarketplaceCredentials { }
