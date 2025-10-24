package io.datapulse.core.exception;

import java.io.Serial;
import java.text.MessageFormat;

public class AppException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = -4039440706715963172L;

  private String userMessage;

  public AppException(Throwable cause, String msgPattern, Object... args) {
    super(MessageFormat.format(msgPattern, args), cause);
  }

  public AppException(String msgPattern, Object... args) {
    super(MessageFormat.format(msgPattern, args));
  }

  public AppException withUserMessage(String msgPattern, Object... args) {
    this.userMessage = MessageFormat.format(msgPattern, args);
    return this;
  }

  public String getUserMessage() {
    return this.userMessage;
  }
}
