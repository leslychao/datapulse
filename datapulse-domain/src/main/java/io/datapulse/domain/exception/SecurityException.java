package io.datapulse.domain.exception;

import io.datapulse.domain.MessageCodes;
import org.springframework.http.HttpStatus;

public class SecurityException extends AppException {

  private SecurityException(HttpStatus status, String messageKey, Object... args) {
    super(status, messageKey, args);
  }

  private SecurityException(Throwable cause, HttpStatus status, String messageKey, Object... args) {
    super(cause, status, messageKey, args);
  }

  public static SecurityException unauthenticatedJwtNotFound() {
    return new SecurityException(
        HttpStatus.UNAUTHORIZED,
        MessageCodes.SECURITY_JWT_AUTHENTICATION_REQUIRED
    );
  }

  public static SecurityException jwtNotAuthenticated() {
    return new SecurityException(
        HttpStatus.UNAUTHORIZED,
        MessageCodes.SECURITY_JWT_NOT_AUTHENTICATED
    );
  }

  public static SecurityException jwtClaimMissing(String claimName) {
    return new SecurityException(
        HttpStatus.UNAUTHORIZED,
        MessageCodes.SECURITY_JWT_CLAIM_REQUIRED,
        claimName
    );
  }

  public static SecurityException userProfileNotResolved() {
    return new SecurityException(
        HttpStatus.FORBIDDEN,
        MessageCodes.SECURITY_USER_PROFILE_NOT_RESOLVED
    );
  }
}
