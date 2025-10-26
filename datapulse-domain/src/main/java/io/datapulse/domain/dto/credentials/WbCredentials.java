package io.datapulse.domain.dto.credentials;

import jakarta.validation.constraints.NotBlank;

public record WbCredentials(
    @NotBlank(message = "{validation.credentials.wb.token.notBlank}") String token) implements
    MarketplaceCredentials {

}
