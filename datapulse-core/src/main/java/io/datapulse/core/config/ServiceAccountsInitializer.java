package io.datapulse.core.config;

import io.datapulse.core.properties.SandboxAccountProperties;
import io.datapulse.core.properties.SystemAccountProperties;
import io.datapulse.core.service.account.AccountConnectionService;
import io.datapulse.core.service.account.AccountService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.request.account.AccountCreateRequest;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import io.datapulse.domain.response.account.AccountResponse;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceAccountsInitializer {

  private final SystemAccountProperties systemAccountProperties;
  private final SandboxAccountProperties sandboxAccountProperties;

  private final AccountService accountService;
  private final AccountConnectionService accountConnectionService;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void init() {
    initAccount(
        "SYSTEM",
        systemAccountProperties.isEnabled(),
        systemAccountProperties.getName(),
        systemAccountProperties.getConnections(),
        SystemAccountProperties.ConnectionProperties::isActive,
        SystemAccountProperties.ConnectionProperties::getToken,
        SystemAccountProperties.ConnectionProperties::getClientId,
        SystemAccountProperties.ConnectionProperties::getApiKey
    );

    initAccount(
        "SANDBOX",
        sandboxAccountProperties.isEnabled(),
        sandboxAccountProperties.getName(),
        sandboxAccountProperties.getConnections(),
        SandboxAccountProperties.ConnectionProperties::isActive,
        SandboxAccountProperties.ConnectionProperties::getToken,
        SandboxAccountProperties.ConnectionProperties::getClientId,
        SandboxAccountProperties.ConnectionProperties::getApiKey
    );
  }

  private <C> void initAccount(
      String kind,
      boolean enabled,
      String rawAccountName,
      Map<MarketplaceType, C> connections,
      Function<C, Boolean> active,
      Function<C, String> token,
      Function<C, String> clientId,
      Function<C, String> apiKey
  ) {
    if (!enabled) {
      log.info("{} account initialization skipped: disabled", kind);
      return;
    }

    String accountName = StringUtils.trimToNull(rawAccountName);
    if (accountName == null) {
      throw new AppException(MessageCodes.ACCOUNT_NAME_REQUIRED);
    }

    if (connections == null || connections.isEmpty()) {
      log.info("{} account initialization skipped: no connections configured", kind);
      return;
    }

    Long accountId = resolveAccountId(kind, accountName);

    for (Map.Entry<MarketplaceType, C> entry : connections.entrySet()) {
      MarketplaceType marketplace = entry.getKey();
      C cfg = entry.getValue();

      if (marketplace == null || cfg == null) {
        continue;
      }
      if (!Boolean.TRUE.equals(active.apply(cfg))) {
        continue;
      }

      ensureConnection(
          kind,
          accountId,
          marketplace,
          token.apply(cfg),
          clientId.apply(cfg),
          apiKey.apply(cfg)
      );
    }
  }

  private Long resolveAccountId(String kind, String accountName) {
    AccountResponse existing = accountService.getActive()
        .stream()
        .filter(a -> StringUtils.equalsIgnoreCase(a.name(), accountName))
        .findFirst()
        .orElse(null);

    if (existing != null) {
      log.info("{} account already exists: id={}, name={}", kind, existing.id(), existing.name());
      return existing.id();
    }

    AccountResponse created = accountService.createFromRequest(
        new AccountCreateRequest(accountName, Boolean.TRUE)
    );

    log.info("{} account created: id={}, name={}", kind, created.id(), created.name());
    return created.id();
  }

  private void ensureConnection(
      String kind,
      Long accountId,
      MarketplaceType marketplace,
      String rawToken,
      String rawClientId,
      String rawApiKey
  ) {
    MarketplaceCredentials credentials =
        buildCredentials(marketplace, rawToken, rawClientId, rawApiKey);

    accountConnectionService.getByAccountAndMarketplace(accountId, marketplace)
        .ifPresentOrElse(
            existing -> activateIfNeeded(kind, accountId, existing, credentials),
            () -> createConnection(kind, accountId, marketplace, credentials)
        );
  }

  private void activateIfNeeded(
      String kind,
      Long accountId,
      AccountConnectionResponse existing,
      MarketplaceCredentials credentials
  ) {
    Boolean active = existing.active();
    if (Boolean.TRUE.equals(active)) {
      return;
    }

    AccountConnectionUpdateRequest request = new AccountConnectionUpdateRequest(
        credentials,
        Boolean.TRUE);
    accountConnectionService.update(accountId, existing.id(), request);

    log.info("{} account connection activated: accountId={}, marketplace={}, connectionId={}",
        kind, accountId, existing.marketplace(), existing.id());
  }

  private void createConnection(
      String kind,
      Long accountId,
      MarketplaceType marketplace,
      MarketplaceCredentials credentials
  ) {
    AccountConnectionCreateRequest request = new AccountConnectionCreateRequest(marketplace,
        credentials);
    accountConnectionService.create(accountId, request);

    log.info("{} account connection created: accountId={}, marketplace={}", kind, accountId,
        marketplace);
  }

  private MarketplaceCredentials buildCredentials(
      MarketplaceType marketplace,
      String rawToken,
      String rawClientId,
      String rawApiKey
  ) {
    return switch (marketplace) {
      case WILDBERRIES -> buildWbCredentials(rawToken);
      case OZON -> buildOzonCredentials(rawClientId, rawApiKey);
    };
  }

  private WbCredentials buildWbCredentials(String rawToken) {
    String token = StringUtils.trimToNull(rawToken);
    if (token == null) {
      throw new AppException(MessageCodes.WB_MISSING_TOKEN);
    }
    return new WbCredentials(token);
  }

  private OzonCredentials buildOzonCredentials(String rawClientId, String rawApiKey) {
    String clientId = StringUtils.trimToNull(rawClientId);
    String apiKey = StringUtils.trimToNull(rawApiKey);

    if (clientId == null || apiKey == null) {
      throw new AppException(MessageCodes.OZON_MISSING_CREDENTIALS);
    }
    return new OzonCredentials(clientId, apiKey);
  }
}
