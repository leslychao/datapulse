package io.datapulse.adapter.marketplaces.support;

public final class MarketplaceExceptions {

  private MarketplaceExceptions() {
  }

  public static class ConfigMissing extends RuntimeException {

    public ConfigMissing(String message) {
      super(message);
    }
  }

  public static class FetchFailed extends RuntimeException {

    public FetchFailed(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class ParseFailed extends RuntimeException {

    public ParseFailed(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
