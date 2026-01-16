package io.datapulse.core.service.account;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;

abstract class AbstractNamedAccountIdProvider {

  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(12);
  private static final long DEFAULT_CACHE_TTL_MILLIS = DEFAULT_CACHE_TTL.toMillis();

  private final AccountService accountService;
  private final long cacheTtlMillis;
  private final AtomicReference<CacheEntry> cache;

  protected AbstractNamedAccountIdProvider(AccountService accountService) {
    this(accountService, DEFAULT_CACHE_TTL_MILLIS);
  }

  protected AbstractNamedAccountIdProvider(AccountService accountService, long cacheTtlMillis) {
    this.accountService = Objects.requireNonNull(accountService, "accountService");
    if (cacheTtlMillis <= 0) {
      throw new IllegalArgumentException("cacheTtlMillis must be positive");
    }
    this.cacheTtlMillis = cacheTtlMillis;
    this.cache = new AtomicReference<>();
  }

  protected final long resolveAccountId(String configuredAccountName, String propertyPathHint) {
    String name = StringUtils.trimToNull(configuredAccountName);
    if (name == null) {
      throw new AppException(MessageCodes.ACCOUNT_NAME_CONFIG_REQUIRED, propertyPathHint);
    }

    long nowMillis = System.currentTimeMillis();

    CacheEntry cached = cache.get();
    if (cached != null && cached.isValidFor(name, nowMillis)) {
      return cached.accountId();
    }

    Long accountId = accountService.findActiveAccountIdByNameIgnoreCase(name);
    if (accountId == null) {
      throw new AppException(MessageCodes.ACCOUNT_NOT_FOUND, name);
    }

    cache.set(new CacheEntry(name, accountId, nowMillis + cacheTtlMillis));
    return accountId;
  }

  protected record CacheEntry(String accountName, long accountId, long expiresAtMillis) {

    boolean isValidFor(String accountName, long nowMillis) {
      return this.accountName.equalsIgnoreCase(accountName) && nowMillis < expiresAtMillis;
    }
  }
}
