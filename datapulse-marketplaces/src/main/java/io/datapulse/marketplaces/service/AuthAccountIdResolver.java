package io.datapulse.marketplaces.service;

import io.datapulse.marketplaces.endpoint.EndpointAuthScope;

public interface AuthAccountIdResolver {

  long resolveAuthAccountId(EndpointAuthScope scope, long targetAccountId);
}
