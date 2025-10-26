package io.datapulse.domain.validation;

import io.datapulse.domain.exception.AppException;
import jakarta.validation.ConstraintValidatorContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

public final class ValidationUtils {

  private ValidationUtils() {
  }

  public static boolean fail(ConstraintValidatorContext ctx, String messageKey) {
    return fail(ctx, messageKey, Map.of());
  }

  public static boolean fail(
      ConstraintValidatorContext ctx,
      String messageKey,
      Map<String, ?> params
  ) {
    ctx.disableDefaultConstraintViolation();
    HibernateConstraintValidatorContext hctx = ctx.unwrap(HibernateConstraintValidatorContext.class);
    if (params != null && !params.isEmpty()) {
      params.forEach((k, v) -> hctx.addMessageParameter(String.valueOf(k), v));
    }
    hctx.buildConstraintViolationWithTemplate("{" + messageKey + "}").addConstraintViolation();
    return false;
  }

  public static boolean fail(ConstraintValidatorContext ctx, String messageKey, Object... params) {
    return fail(ctx, messageKey, toParamMap(params));
  }

  private static Map<String, Object> toParamMap(Object... params) {
    if (params == null || params.length == 0) {
      return Map.of();
    }
    if (params.length % 2 != 0) {
      throw new AppException("params.must-be-key-value-pairs", params.length);
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < params.length; i += 2) {
      map.put(String.valueOf(params[i]), params[i + 1]);
    }
    return map;
  }
}
