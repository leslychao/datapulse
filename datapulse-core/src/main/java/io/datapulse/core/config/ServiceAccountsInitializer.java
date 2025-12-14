package io.datapulse.core.config;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.AccountService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.NotFoundException;
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

      createConnectionIfMissing(
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
    AccountDto existing = accountService.getActive()
        .stream()
        .filter(a -> StringUtils.equalsIgnoreCase(a.getName(), accountName))
        .findFirst()
        .orElse(null);

    if (existing != null) {
      log.info("{} account already exists: id={}, name={}", kind, existing.getId(), existing.getName());
      return existing.getId();
    }

    AccountResponse created = accountService.createFromRequest(
        new AccountCreateRequest(accountName, Boolean.TRUE)
    );

    log.info("{} account created: id={}, name={}", kind, created.id(), created.name());
    return created.id();
  }

  private void createConnectionIfMissing(
      String kind,
      Long accountId,
      MarketplaceType marketplace,
      String rawToken,
      String rawClientId,
      String rawApiKey
  ) {
    if (connectionExists(accountId, marketplace)) {
      return;
    }

    AccountConnectionCreateRequest request = buildConnectionRequest(accountId, marketplace, rawToken, rawClientId, rawApiKey);

    accountConnectionService.createFromRequest(request);
    log.info("{} account connection created: accountId={}, marketplace={}", kind, accountId, marketplace);
  }

  private AccountConnectionCreateRequest buildConnectionRequest(
      Long accountId,
      MarketplaceType marketplace,
      String rawToken,
      String rawClientId,
      String rawApiKey
  ) {
    return switch (marketplace) {
      case WILDBERRIES -> buildWbRequest(accountId, marketplace, rawToken);
      case OZON -> buildOzonRequest(accountId, marketplace, rawClientId, rawApiKey);
    };
  }

  private boolean connectionExists(Long accountId, MarketplaceType marketplace) {
    try {
      accountConnectionService.assertActiveConnectionExists(accountId, marketplace);
      return true;
    } catch (NotFoundException ex) {
      return false;
    }
  }

  private AccountConnectionCreateRequest buildWbRequest(
      Long accountId,
      MarketplaceType marketplace,
      String rawToken
  ) {
    String token = StringUtils.trimToNull(rawToken);
    if (token == null) {
      throw new AppException(MessageCodes.WB_MISSING_TOKEN);
    }

    return new AccountConnectionCreateRequest(
        accountId,
        marketplace,
        new WbCredentials(token),
        Boolean.TRUE
    );
  }

  private AccountConnectionCreateRequest buildOzonRequest(
      Long accountId,
      MarketplaceType marketplace,
      String rawClientId,
      String rawApiKey
  ) {
    String clientId = StringUtils.trimToNull(rawClientId);
    String apiKey = StringUtils.trimToNull(rawApiKey);

    if (clientId == null || apiKey == null) {
      throw new AppException(MessageCodes.OZON_MISSING_CREDENTIALS);
    }

    return new AccountConnectionCreateRequest(
        accountId,
        marketplace,
        new OzonCredentials(clientId, apiKey),
        Boolean.TRUE
    );
  }
}
