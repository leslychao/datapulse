package io.datapulse.integration.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(VaultProperties.class)
public class VaultConfig {

    @Bean
    public VaultTemplate vaultTemplate(VaultProperties properties) {
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(properties.getUri()));
        return new VaultTemplate(endpoint, new TokenAuthentication(properties.getToken()));
    }

    @Bean
    public VaultKeyValueOperations vaultKeyValueOperations(VaultTemplate vaultTemplate) {
        return vaultTemplate.opsForKeyValue("secret", VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
    }

    @Bean("credentialCache")
    public Cache<String, Map<String, String>> credentialCache(VaultProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(properties.getCredentialCacheTtl())
                .build();
    }
}
