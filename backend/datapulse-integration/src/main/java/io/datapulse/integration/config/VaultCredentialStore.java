package io.datapulse.integration.config;

import com.github.benmanes.caffeine.cache.Cache;
import io.datapulse.common.exception.AppException;
import io.datapulse.integration.domain.CredentialStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.Versioned;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultCredentialStore implements CredentialStore {

    private static final int VAULT_UNAVAILABLE_STATUS = 503;

    private final VaultVersionedKeyValueOperations vaultKv;
    private final Cache<String, Map<String, String>> credentialCache;

    @Override
    public int store(String vaultPath, String vaultKey, Map<String, String> credentials) {
        Map<String, Object> secretData = new HashMap<>();
        secretData.put(vaultKey, credentials);

        try {
            Versioned.Metadata metadata = vaultKv.put(vaultPath, secretData);
            int version = metadata.getVersion().getVersion();
            log.info("Credentials stored: path={}, key={}, version={}", vaultPath, vaultKey, version);

            String cacheKey = buildCacheKey(vaultPath, vaultKey);
            credentialCache.put(cacheKey, Collections.unmodifiableMap(new HashMap<>(credentials)));

            return version;
        } catch (VaultException e) {
            log.error("Vault store failed: path={}, key={}", vaultPath, vaultKey, e);
            throw new AppException("vault.unavailable", VAULT_UNAVAILABLE_STATUS, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked") // safe: Vault KV stores Map<String, Object>, we only write Map<String, String>
    public Map<String, String> read(String vaultPath, String vaultKey) {
        String cacheKey = buildCacheKey(vaultPath, vaultKey);

        Map<String, String> cached = credentialCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Credentials cache hit: path={}, key={}", vaultPath, vaultKey);
            return cached;
        }

        try {
            Versioned<Map<String, Object>> versioned = vaultKv.get(vaultPath);
            if (versioned == null || versioned.getData() == null) {
                throw new AppException("vault.secret.not.found", 404, vaultPath);
            }

            Object raw = versioned.getData().get(vaultKey);
            if (!(raw instanceof Map<?, ?> rawMap)) {
                throw new AppException("vault.secret.not.found", 404, vaultPath, vaultKey);
            }

            Map<String, String> credentials = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                credentials.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }

            Map<String, String> immutable = Collections.unmodifiableMap(credentials);
            credentialCache.put(cacheKey, immutable);

            log.debug("Credentials loaded from Vault: path={}, key={}", vaultPath, vaultKey);
            return immutable;
        } catch (VaultException e) {
            log.error("Vault read failed: path={}, key={}", vaultPath, vaultKey, e);
            throw new AppException("vault.unavailable", VAULT_UNAVAILABLE_STATUS, e);
        }
    }

    @Override
    public int rotate(String vaultPath, String vaultKey, Map<String, String> newCredentials) {
        evictCache(vaultPath, vaultKey);
        int version = store(vaultPath, vaultKey, newCredentials);
        log.info("Credentials rotated: path={}, key={}, newVersion={}", vaultPath, vaultKey, version);
        return version;
    }

    @Override
    public void evictCache(String vaultPath, String vaultKey) {
        String cacheKey = buildCacheKey(vaultPath, vaultKey);
        credentialCache.invalidate(cacheKey);
        log.debug("Credential cache evicted: path={}, key={}", vaultPath, vaultKey);
    }

    private String buildCacheKey(String vaultPath, String vaultKey) {
        return vaultPath + ":" + vaultKey;
    }
}
