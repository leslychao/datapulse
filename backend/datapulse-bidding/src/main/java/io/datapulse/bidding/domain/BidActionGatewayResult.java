package io.datapulse.bidding.domain;

public record BidActionGatewayResult(
    boolean success,
    Integer appliedBid,
    String errorCode,
    String errorMessage,
    String rawResponse
) {

  public static BidActionGatewayResult success(Integer appliedBid, String rawResponse) {
    return new BidActionGatewayResult(true, appliedBid, null, null, rawResponse);
  }

  public static BidActionGatewayResult failure(String errorCode, String errorMessage,
      String rawResponse) {
    return new BidActionGatewayResult(false, null, errorCode, errorMessage, rawResponse);
  }
}
