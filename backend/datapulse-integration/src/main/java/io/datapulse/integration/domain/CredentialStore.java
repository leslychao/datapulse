package io.datapulse.integration.domain;

import java.util.Map;

public interface CredentialStore {

    /**
     * Store credentials at the given Vault path.
     *
     * @return Vault KV version number of the written secret
     */
    int store(String vaultPath, String vaultKey, Map<String, String> credentials);

    /**
     * Read credentials from the given Vault path (cache-first).
     */
    Map<String, String> read(String vaultPath, String vaultKey);

    /**
     * Write new credentials version (Vault KV-v2 auto-versions), evict cache.
     *
     * @return new Vault KV version number
     */
    int rotate(String vaultPath, String vaultKey, Map<String, String> newCredentials);

    /**
     * Evict cached credentials (e.g. after rotation event from another instance).
     */
    void evictCache(String vaultPath, String vaultKey);
}
