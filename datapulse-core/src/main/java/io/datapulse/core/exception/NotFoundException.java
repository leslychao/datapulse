package io.datapulse.core.exception;

public class NotFoundException extends AppException {

  public NotFoundException(String msgPattern, Object... argArray) {
    super(msgPattern, argArray);
  }
}
