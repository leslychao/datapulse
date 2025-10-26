package io.datapulse.domain.dto.credentials;

import jakarta.validation.constraints.NotBlank;

public record OzonCredentials(
    @NotBlank(message = "{validation.credentials.ozon.clientId.notBlank}")
    String clientId,
    @NotBlank(message = "{validation.credentials.ozon.apiKey.notBlank}")
    String apiKey
) implements MarketplaceCredentials {

}
