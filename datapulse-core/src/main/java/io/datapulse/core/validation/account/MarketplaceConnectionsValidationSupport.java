package io.datapulse.core.validation.account;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.ValidationKeys;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

final class MarketplaceConnectionsValidationSupport {

  private MarketplaceConnectionsValidationSupport() {
  }

  static <C> boolean validate(
      boolean enabled,
      Map<MarketplaceType, C> connections,
      Function<C, Boolean> active,
      Function<C, String> token,
      Function<C, String> clientId,
      Function<C, String> apiKey,
      ConstraintValidatorContext context
  ) {
    if (!enabled || connections == null || connections.isEmpty()) {
      return true;
    }

    boolean valid = true;
    boolean defaultViolationDisabled = false;

    for (Map.Entry<MarketplaceType, C> entry : connections.entrySet()) {
      MarketplaceType marketplace = entry.getKey();
      C cfg = entry.getValue();

      if (marketplace == null || cfg == null) {
        continue;
      }
      if (!Boolean.TRUE.equals(active.apply(cfg))) {
        continue;
      }

      boolean ok = validateByMarketplace(
          context,
          marketplace,
          token.apply(cfg),
          clientId.apply(cfg),
          apiKey.apply(cfg),
          defaultViolationDisabled
      );

      if (!ok) {
        valid = false;
        defaultViolationDisabled = true;
      }
    }

    return valid;
  }

  private static boolean validateByMarketplace(
      ConstraintValidatorContext context,
      MarketplaceType marketplace,
      String token,
      String clientId,
      String apiKey,
      boolean defaultViolationDisabled
  ) {
    return switch (marketplace) {
      case WILDBERRIES -> {
        if (StringUtils.isNotBlank(token)) {
          yield true;
        }
        addViolation(context, marketplace, "token",
            ValidationKeys.CREDENTIALS_WB_TOKEN_NOT_BLANK, defaultViolationDisabled);
        yield false;
      }
      case OZON -> {
        boolean ok = true;

        if (StringUtils.isBlank(clientId)) {
          addViolation(context, marketplace, "clientId",
              ValidationKeys.CREDENTIALS_OZON_CLIENT_ID_NOT_BLANK, defaultViolationDisabled);
          ok = false;
          defaultViolationDisabled = true;
        }

        if (StringUtils.isBlank(apiKey)) {
          addViolation(context, marketplace, "apiKey",
              ValidationKeys.CREDENTIALS_OZON_API_KEY_NOT_BLANK, defaultViolationDisabled);
          ok = false;
        }

        yield ok;
      }
    };
  }

  private static void addViolation(
      ConstraintValidatorContext context,
      MarketplaceType marketplace,
      String field,
      String message,
      boolean defaultViolationDisabled
  ) {
    if (!defaultViolationDisabled) {
      context.disableDefaultConstraintViolation();
    }

    context.buildConstraintViolationWithTemplate(message)
        .addPropertyNode("connections")
        .inIterable().atKey(marketplace)
        .addPropertyNode(field)
        .addConstraintViolation();
  }
}
