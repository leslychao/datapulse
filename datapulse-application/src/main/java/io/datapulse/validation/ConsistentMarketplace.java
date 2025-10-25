package io.datapulse.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MarketplaceCredentialsValidator.class)
@Documented
public @interface ConsistentMarketplace {

  String message() default "${validation.marketplace.credentials.type.mismatch}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
