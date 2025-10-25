package io.datapulse.validation;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.request.AccountConnectionCreateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Objects;

public class MarketplaceCredentialsValidator
    implements ConstraintValidator<ConsistentMarketplace, AccountConnectionCreateRequest> {

  private static final Map<MarketplaceType, String> EXPECTED_TYPES = Map.of(
      MarketplaceType.WILDBERRIES, "WB",
      MarketplaceType.OZON, "OZON"
  );

  @Override
  public boolean isValid(AccountConnectionCreateRequest req, ConstraintValidatorContext ctx) {
    if (req == null || req.getMarketplaceType() == null || req.getCredentials() == null) {
      return true;
    }

    var expected = EXPECTED_TYPES.get(req.getMarketplaceType());
    var actualRaw = req.getCredentials().type();
    var actual = actualRaw == null ? null : actualRaw.trim().toUpperCase();

    boolean ok = Objects.equals(expected, actual);
    if (!ok) {
      return ValidationUtils.fail(
          ctx,
          "validation.marketplace.credentials.type.mismatch",
          "expected", req.getMarketplaceType(),
          "actual", actual
      );
    }
    return true;
  }
}
