package io.datapulse.marketplaces.service;

import io.datapulse.core.client.HttpStreamingClient;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.resilience.MarketplaceRateLimiter;
import io.datapulse.marketplaces.resilience.MarketplaceRetryService;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class MarketplaceStreamingDownloadService {

  private final HttpStreamingClient httpStreamingClient;
  private final FileStreamingService fileStreamingService;
  private final MarketplaceRetryService retryService;
  private final MarketplaceRateLimiter rateLimiter;

  public Path download(
      MarketplaceType marketplace,
      EndpointKey endpoint,
      long accountId,
      HttpMethod method,
      URI uri,
      HttpHeaders headers,
      Map<String, ?> body,
      Path targetFile
  ) {
    Flux<DataBuffer> source;

    if (method == HttpMethod.GET) {
      source = Flux.defer(() -> {
        rateLimiter.ensurePermit(marketplace, endpoint, accountId);
        return httpStreamingClient.getAsDataBufferFlux(uri, headers);
      });
    } else if (method == HttpMethod.POST) {
      source = Flux.defer(() -> {
        rateLimiter.ensurePermit(marketplace, endpoint, accountId);
        return httpStreamingClient.postAsDataBufferFlux(
            uri,
            headers,
            body != null ? body : Map.of()
        );
      });
    } else {
      throw new IllegalArgumentException("Unsupported HTTP method: " + method);
    }

    Flux<DataBuffer> retried = retryService.withRetries(source, marketplace, endpoint);

    return fileStreamingService.writeToPermanentFile(retried, targetFile);
  }
}
