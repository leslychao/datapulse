package io.datapulse.marketplaces.service;

import io.datapulse.core.service.account.SandboxAccountIdProvider;
import io.datapulse.core.service.account.SystemAccountIdProvider;
import io.datapulse.marketplaces.endpoint.EndpointAuthScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultAuthAccountIdResolver implements AuthAccountIdResolver {

  private final SystemAccountIdProvider systemAccountIdProvider;
  private final SandboxAccountIdProvider sandboxAccountIdProvider;

  @Override
  public long resolveAuthAccountId(EndpointAuthScope scope, long targetAccountId) {
    return switch (scope) {
      case TARGET_ACCOUNT -> targetAccountId;
      case SANDBOX_ACCOUNT -> sandboxAccountIdProvider.getSandboxAccountId();
      case SYSTEM_ACCOUNT -> systemAccountIdProvider.getSystemAccountId();
    };
  }
}
