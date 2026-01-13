package io.datapulse.core.validation.account;

import io.datapulse.core.config.SandboxAccountProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class SandboxMarketplaceConnectionsValidator
    implements ConstraintValidator<ValidMarketplaceConnections, SandboxAccountProperties> {

  @Override
  public boolean isValid(SandboxAccountProperties value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return MarketplaceConnectionsValidationSupport.validate(
        value.isEnabled(),
        value.getConnections(),
        SandboxAccountProperties.ConnectionProperties::isActive,
        SandboxAccountProperties.ConnectionProperties::getToken,
        SandboxAccountProperties.ConnectionProperties::getClientId,
        SandboxAccountProperties.ConnectionProperties::getApiKey,
        context
    );
  }
}
