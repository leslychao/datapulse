package io.datapulse.core.service.account;

import io.datapulse.core.config.SystemAccountProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultSystemAccountIdProvider extends AbstractNamedAccountIdProvider
    implements SystemAccountIdProvider {

  private final SystemAccountProperties properties;

  public DefaultSystemAccountIdProvider(
      AccountService accountService,
      SystemAccountProperties properties) {
    super(accountService);
    this.properties = properties;
  }

  @Override
  public long getSystemAccountId() {
    return resolveAccountId(properties.getName(), "system-account.name");
  }
}
