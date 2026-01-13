package io.datapulse.core.validation.account;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.datapulse.domain.ValidationKeys;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {
    SystemMarketplaceConnectionsValidator.class,
    SandboxMarketplaceConnectionsValidator.class
})
public @interface ValidMarketplaceConnections {

  String message() default ValidationKeys.ACCOUNT_CONNECTION_CREDENTIALS_REQUIRED;

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
