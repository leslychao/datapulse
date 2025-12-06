package io.datapulse.core.config;

import io.datapulse.core.service.AccountConnectionService;
import io.datapulse.core.service.AccountService;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.credentials.OzonCredentials;
import io.datapulse.domain.dto.credentials.WbCredentials;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAccountInitializer {

  private final SystemAccountProperties properties;
  private final AccountService accountService;
  private final AccountConnectionService accountConnectionService;

  @PostConstruct
  @Transactional
  public void init() {
    if (!properties.isEnabled()) {
      return;
    }

    String accountName = StringUtils.trimToNull(properties.getName());
    if (accountName == null) {
      log.warn("System account initialization skipped: name not configured");
      return;
    }

    Map<MarketplaceType, SystemAccountProperties.ConnectionProperties> connections =
        properties.getConnections();
    if (connections == null || connections.isEmpty()) {
      log.info("System account initialization skipped: no connections configured");
      return;
    }

    Long accountId = resolveAccountId(accountName);

    for (Map.Entry<MarketplaceType, SystemAccountProperties.ConnectionProperties> entry
        : connections.entrySet()) {

      MarketplaceType marketplace = entry.getKey();
      SystemAccountProperties.ConnectionProperties cfg = entry.getValue();

      if (cfg == null || !cfg.isActive()) {
        continue;
      }

      createConnectionIfMissing(accountId, marketplace, cfg);
    }
  }

  private Long resolveAccountId(String accountName) {
    AccountDto existing = accountService.getActive()
        .stream()
        .filter(a -> StringUtils.equalsIgnoreCase(a.getName(), accountName))
        .findFirst()
        .orElse(null);

    if (existing != null) {
      log.info("System account already exists: id={}, name={}", existing.getId(),
          existing.getName());
      return existing.getId();
    }

    AccountCreateRequest request = new AccountCreateRequest(accountName, Boolean.TRUE);
    AccountResponse created = accountService.createFromRequest(request);

    log.info("System account created: id={}, name={}", created.id(), created.name());
    return created.id();
  }

  private void createConnectionIfMissing(
      Long accountId,
      MarketplaceType marketplace,
      SystemAccountProperties.ConnectionProperties cfg
  ) {
    if (connectionExists(accountId, marketplace)) {
      return;
    }

    AccountConnectionCreateRequest request = switch (marketplace) {
      case WILDBERRIES -> buildWbRequest(accountId, cfg);
      case OZON -> buildOzonRequest(accountId, cfg);
    };

    if (request == null) {
      log.warn(
          "System account connection not created: incomplete credentials, accountId={}, marketplace={}",
          accountId,
          marketplace
      );
      return;
    }

    accountConnectionService.createFromRequest(request);
    log.info(
        "System account connection created: accountId={}, marketplace={}",
        accountId,
        marketplace
    );
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
      SystemAccountProperties.ConnectionProperties cfg
  ) {
    String token = StringUtils.trimToNull(cfg.getToken());
    if (token == null) {
      return null;
    }

    WbCredentials credentials = new WbCredentials(token);
    return new AccountConnectionCreateRequest(
        accountId,
        MarketplaceType.WILDBERRIES,
        credentials,
        Boolean.TRUE
    );
  }

  private AccountConnectionCreateRequest buildOzonRequest(
      Long accountId,
      SystemAccountProperties.ConnectionProperties cfg
  ) {
    String clientId = StringUtils.trimToNull(cfg.getClientId());
    String apiKey = StringUtils.trimToNull(cfg.getApiKey());

    if (clientId == null || apiKey == null) {
      return null;
    }

    OzonCredentials credentials = new OzonCredentials(clientId, apiKey);
    return new AccountConnectionCreateRequest(
        accountId,
        MarketplaceType.OZON,
        credentials,
        Boolean.TRUE
    );
  }
}
