package io.datapulse.domain.exception;

import io.datapulse.domain.MessageCodes;
import org.springframework.http.HttpStatus;

public class InternalServerErrorException extends AppException {

  private InternalServerErrorException(Throwable cause, String messageKey, Object... args) {
    super(cause, HttpStatus.INTERNAL_SERVER_ERROR, messageKey, args);
  }

  public static InternalServerErrorException inviteTokenHashingFailed(Throwable cause) {
    return new InternalServerErrorException(cause, MessageCodes.INVITE_TOKEN_HASHING_FAILED);
  }
}
