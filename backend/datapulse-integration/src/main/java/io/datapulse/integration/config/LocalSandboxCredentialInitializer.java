package io.datapulse.integration.config;

import io.datapulse.integration.domain.CredentialStore;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.SecretReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * On startup with local profile, replaces WB credentials in Vault
 * with the sandbox token so that ETL reads from WB sandbox APIs.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSandboxCredentialInitializer implements ApplicationRunner {

  private final IntegrationProperties properties;
  private final MarketplaceConnectionRepository connectionRepository;
  private final SecretReferenceRepository secretReferenceRepository;
  private final CredentialStore credentialStore;

  @Override
  public void run(ApplicationArguments args) {
    String sandboxToken = properties.getWildberries().getSandboxApiToken();
    if (sandboxToken == null || sandboxToken.isBlank()) {
      log.debug("WB sandbox-api-token not configured, skipping credential provisioning");
      return;
    }

    var wbConnections = connectionRepository.findAllByMarketplaceType("WB");
    if (wbConnections.isEmpty()) {
      log.info("No WB connections found, skipping sandbox credential provisioning");
      return;
    }

    Map<String, String> sandboxCredentials = Map.of("apiToken", sandboxToken);

    for (var connection : wbConnections) {
      secretReferenceRepository.findById(connection.getSecretReferenceId())
          .ifPresentOrElse(
              secretRef -> {
                credentialStore.store(
                    secretRef.getVaultPath(), secretRef.getVaultKey(), sandboxCredentials);
                log.info("Provisioned WB sandbox token: connectionId={}, vaultPath={}",
                    connection.getId(), secretRef.getVaultPath());
              },
              () -> log.warn("Secret reference not found for WB connection: id={}",
                  connection.getId()));
    }
  }
}
