package io.datapulse.core.service.account;

import io.datapulse.core.config.SandboxAccountProperties;
import io.datapulse.core.service.AccountService;
import org.springframework.stereotype.Component;

@Component
public class DefaultSandboxAccountIdProvider extends AbstractNamedAccountIdProvider
    implements SandboxAccountIdProvider {

  private final SandboxAccountProperties properties;

  public DefaultSandboxAccountIdProvider(
      AccountService accountService,
      SandboxAccountProperties properties) {
    super(accountService);
    this.properties = properties;
  }

  @Override
  public long getSandboxAccountId() {
    return resolveAccountId(properties.getName(), "sandbox-account.name");
  }
}
