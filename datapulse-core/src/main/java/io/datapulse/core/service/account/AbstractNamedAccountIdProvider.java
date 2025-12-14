package io.datapulse.core.service.account;

import io.datapulse.core.service.AccountService;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.dto.LongBaseDto;
import io.datapulse.domain.exception.AppException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;

abstract class AbstractNamedAccountIdProvider {

  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(12);

  private final AccountService accountService;
  private final Clock clock;
  private final AtomicReference<CacheEntry> cache;

  protected AbstractNamedAccountIdProvider(AccountService accountService) {
    this(accountService, Clock.systemUTC());
  }

  protected AbstractNamedAccountIdProvider(AccountService accountService, Clock clock) {
    this.accountService = Objects.requireNonNull(accountService, "accountService");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.cache = new AtomicReference<>();
  }

  protected final long resolveAccountId(String configuredAccountName, String propertyPathHint) {
    CacheEntry cached = cache.get();
    Instant now = clock.instant();
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.accountId();
    }

    String name = StringUtils.trimToNull(configuredAccountName);
    if (name == null) {
      throw new AppException(MessageCodes.ACCOUNT_NAME_REQUIRED, propertyPathHint);
    }

    long resolvedId = accountService.getActive()
        .stream()
        .filter(account -> StringUtils.equalsIgnoreCase(account.getName(), name))
        .findFirst()
        .map(LongBaseDto::getId)
        .orElseThrow(() -> new AppException(MessageCodes.ACCOUNT_NOT_FOUND, name));

    cache.set(new CacheEntry(resolvedId, now.plus(DEFAULT_CACHE_TTL)));
    return resolvedId;
  }

  protected record CacheEntry(long accountId, Instant expiresAt) {

  }
}
