package io.datapulse.marketplaces.resilience;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

@Slf4j
public class WbRetryPolicy extends BaseRetryPolicy {

  @Override
  protected Duration computeHeaderDelay(HttpHeaders headers, int status) {
    if (status == STATUS_TOO_MANY_REQUESTS || status == STATUS_SERVICE_UNAVAILABLE) {
      Duration xr = parseSeconds(headers.getFirst(HDR_X_RETRY));
      if (xr != null) {
        return xr;
      }

      Duration ra = parseRetryAfter(headers);
      if (ra != null && !ra.isNegative()) {
        return ra;
      }

      return null;
    }
    return super.computeHeaderDelay(headers, status);
  }
}
