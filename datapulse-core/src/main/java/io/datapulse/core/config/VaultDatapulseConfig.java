package io.datapulse.core.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;

@Configuration
public class VaultDatapulseConfig {

  private static final String BACKEND = "secret";

  @Bean
  public VaultEndpoint vaultEndpoint(@Value("${vault.uri}") URI uri) {
    return VaultEndpoint.from(uri);
  }

  @Bean
  public ClientAuthentication clientAuthentication(
      @Value("${vault.token}") String token
  ) {
    return new TokenAuthentication(token);
  }

  @Bean
  public VaultTemplate vaultTemplate(
      VaultEndpoint endpoint,
      ClientAuthentication clientAuthentication
  ) {
    return new VaultTemplate(endpoint, clientAuthentication);
  }

  @Bean
  public VaultKeyValueOperations datapulseKv(VaultTemplate vaultTemplate) {
    return vaultTemplate.opsForKeyValue(BACKEND, KeyValueBackend.KV_2);
  }
}
