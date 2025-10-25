package io.datapulse.validation;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.request.AccountConnectionCreateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class MarketplaceCredentialsValidator
    implements ConstraintValidator<ConsistentMarketplace, AccountConnectionCreateRequest> {

  private static final Map<MarketplaceType, String> EXPECTED_TYPES = Map.of(
      MarketplaceType.WILDBERRIES, "WB",
      MarketplaceType.OZON, "OZON"
  );

  @Override
  public boolean isValid(AccountConnectionCreateRequest req, ConstraintValidatorContext ctx) {
    if (req == null || req.getMarketplace() == null || req.getCredentials() == null) {
      return true;
    }

    var parsedOpt = MarketplaceParser.parse(req.getMarketplace());
    if (parsedOpt.isEmpty()) {
      return ValidationUtils.fail(
          ctx,
          "validation.marketplace.unknown",
          "value", req.getMarketplace().trim(),
          "allowed", MarketplaceParser.allowedList()
      );
    }

    MarketplaceType marketplace = parsedOpt.get();
    String expectedType = EXPECTED_TYPES.get(marketplace);
    String actualType = normalize(req.getCredentials().type());

    if (!Objects.equals(expectedType, actualType)) {
      return ValidationUtils.fail(
          ctx,
          "validation.marketplace.credentials.type.mismatch",
          "marketplace", marketplace,
          "expected", expectedType,
          "actual", actualType
      );
    }

    return true;
  }

  private static String normalize(String str) {
    return StringUtils.trimToEmpty(str).toUpperCase();
  }
}
