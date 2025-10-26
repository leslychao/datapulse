package io.datapulse.core.service;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.dto.credentials.MarketplaceCredentials;

public interface CredentialsProvider {

  MarketplaceCredentials resolve(long accountId, MarketplaceType type);
}
