package io.datapulse.core.validation.account;

import io.datapulse.core.config.SystemAccountProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class SystemMarketplaceConnectionsValidator
    implements ConstraintValidator<ValidMarketplaceConnections, SystemAccountProperties> {

  @Override
  public boolean isValid(SystemAccountProperties value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return MarketplaceConnectionsValidationSupport.validate(
        value.isEnabled(),
        value.getConnections(),
        SystemAccountProperties.ConnectionProperties::isActive,
        SystemAccountProperties.ConnectionProperties::getToken,
        SystemAccountProperties.ConnectionProperties::getClientId,
        SystemAccountProperties.ConnectionProperties::getApiKey,
        context
    );
  }
}
