package io.datapulse.domain.exception;

public final class MarketplaceExceptions {

  private MarketplaceExceptions() {
  }

  public static class ConfigMissing extends AppException {

    public ConfigMissing(String msgPattern, Object... args) {
      super(msgPattern, args);
    }
  }

  public static class FetchFailed extends AppException {

    public FetchFailed(Throwable cause, String msgPattern, Object... args) {
      super(cause, msgPattern, args);
    }
  }

  public static class ParseFailed extends AppException {

    public ParseFailed(Throwable cause, String msgPattern, Object... args) {
      super(cause, msgPattern, args);
    }
  }
}
