package io.datapulse.domain.exception;

import io.datapulse.domain.MessageCodes;
import org.springframework.http.HttpStatus;

public class InviteException extends AppException {

  private InviteException(HttpStatus status, String messageKey, Object... args) {
    super(status, messageKey, args);
  }

  private InviteException(Throwable cause, HttpStatus status, String messageKey, Object... args) {
    super(cause, status, messageKey, args);
  }

  public static InviteException emailDeliveryFailed(String email, Throwable cause) {
    return new InviteException(
        cause,
        HttpStatus.SERVICE_UNAVAILABLE,
        MessageCodes.INVITE_EMAIL_DELIVERY_FAILED,
        email
    );
  }
}
