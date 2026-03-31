package io.datapulse.integration.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "datapulse.vault")
public class VaultProperties {

    @NotBlank
    private final String uri;

    @NotBlank
    private final String token;

    private final Duration credentialCacheTtl;

    private final String basePath;

    public Duration getCredentialCacheTtl() {
        return credentialCacheTtl != null ? credentialCacheTtl : Duration.ofHours(1);
    }

    public String getBasePath() {
        return basePath != null ? basePath : "secret/data/datapulse";
    }
}
