package io.datapulse.tenancy.domain;

import io.datapulse.platform.audit.AuditPublisher;
import io.datapulse.tenancy.persistence.AppUserEntity;
import io.datapulse.tenancy.persistence.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached user resolution — avoids hitting the DB on every HTTP request and
 * WebSocket handshake. Cache TTL is short (60 s) so status changes propagate
 * quickly, yet the hot-path (same JWT subject repeated dozens of times per
 * minute) is served from memory.
 */
@Slf4j
@Service
public class UserResolverService {

  private static final long TTL_SECONDS = 60;
  private static final int MAX_SIZE = 500;

  private final AppUserRepository appUserRepository;
  private final AuditPublisher auditPublisher;

  private final ConcurrentHashMap<String, CachedUser> cache = new ConcurrentHashMap<>();

  public UserResolverService(AppUserRepository appUserRepository,
                             AuditPublisher auditPublisher) {
    this.appUserRepository = appUserRepository;
    this.auditPublisher = auditPublisher;
  }

  /**
   * Resolves an existing user by {@code externalId} (JWT subject).
   * Returns {@code null} when the user doesn't exist.
   */
  public AppUserEntity resolve(String externalId) {
    CachedUser cached = cache.get(externalId);
    if (cached != null && !cached.isExpired()) {
      return cached.user;
    }

    return appUserRepository.findByExternalId(externalId)
        .map(user -> {
          putCache(externalId, user);
          return user;
        })
        .orElse(null);
  }

  /**
   * Resolves a user by {@code externalId}, creating (provisioning) a new
   * one when not found.  Race-condition safe via catch-and-retry on unique
   * constraint violation.
   */
  public AppUserEntity resolveOrProvision(String externalId, String email,
                                          String displayName) {
    CachedUser cached = cache.get(externalId);
    if (cached != null && !cached.isExpired()) {
      return cached.user;
    }

    AppUserEntity user = appUserRepository.findByExternalId(externalId)
        .orElseGet(() -> {
          try {
            return provisionUser(externalId, email, displayName);
          } catch (DataIntegrityViolationException e) {
            return appUserRepository.findByExternalId(externalId)
                .orElseThrow(() -> e);
          }
        });

    putCache(externalId, user);
    return user;
  }

  private void putCache(String externalId, AppUserEntity user) {
    if (cache.size() >= MAX_SIZE) {
      evictExpired();
    }
    cache.put(externalId, new CachedUser(user, Instant.now()));
  }

  private void evictExpired() {
    cache.entrySet().removeIf(e -> e.getValue().isExpired());
  }

  private AppUserEntity provisionUser(String externalId, String email,
                                      String name) {
    log.info("Auto-provisioning user: externalId={}, email={}", externalId, email);

    var user = new AppUserEntity();
    user.setExternalId(externalId);
    user.setEmail(email);
    user.setName(name != null ? name : email);
    user.setStatus(UserStatus.ACTIVE);
    AppUserEntity saved = appUserRepository.save(user);

    auditPublisher.publishSystem(saved.getId(), "user.provision",
        "app_user", String.valueOf(saved.getId()));

    return saved;
  }

  private record CachedUser(AppUserEntity user, Instant cachedAt) {

    boolean isExpired() {
      return Instant.now().isAfter(cachedAt.plusSeconds(TTL_SECONDS));
    }
  }
}
