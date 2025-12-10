package io.datapulse.marketplaces.adapter;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointRef;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.service.MarketplaceStreamingDownloadService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMarketplaceAdapter {

  private final MarketplaceType marketplaceType;

  protected final MarketplaceStreamingDownloadService downloader;
  protected final HttpHeaderProvider headerProvider;
  protected final EndpointsResolver resolver;
  private final MarketplaceProperties properties;

  protected final <R> Snapshot<R> doGet(
      long accountId,
      EndpointKey key,
      Class<R> elementType
  ) {
    return execute(accountId, key, HttpMethod.GET, null, null, elementType);
  }

  protected final <R> Snapshot<R> doGet(
      long accountId,
      EndpointKey key,
      Map<String, ?> queryParams,
      Class<R> elementType
  ) {
    return execute(accountId, key, HttpMethod.GET, queryParams, null, elementType);
  }

  protected final <R> Snapshot<R> doPost(
      long accountId,
      EndpointKey key,
      Map<String, ?> body,
      Class<R> elementType
  ) {
    return execute(accountId, key, HttpMethod.POST, null, body, elementType);
  }

  private EndpointRef resolveEndpoint(
      EndpointKey key,
      HttpMethod method,
      Map<String, ?> params
  ) {
    if (method == HttpMethod.GET && params != null) {
      return resolver.resolve(marketplaceType, key, params);
    }
    return resolver.resolve(marketplaceType, key);
  }

  private Map<String, ?> safe(Map<String, ?> body) {
    return body != null ? body : Map.of();
  }

  private <R> Snapshot<R> execute(
      long accountId,
      EndpointKey endpointKey,
      HttpMethod method,
      Map<String, ?> queryParams,
      Map<String, ?> body,
      Class<R> elementType
  ) {
    try {
      EndpointRef ref = resolveEndpoint(endpointKey, method, queryParams);
      URI uri = ref.uri();

      HttpHeaders headers = headerProvider.build(marketplaceType, accountId);
      Path target = planPath(accountId, endpointKey);

      if (Files.exists(target)) {
        long size = Files.size(target);
        log.info(
            "Snapshot reused from local cache: marketplace={}, accountId={}, endpoint={}, path={}, sizeBytes={}",
            marketplaceType, accountId, endpointKey, target, size
        );
        return new Snapshot<>(elementType, target);
      }

      log.info(
          "Starting snapshot download: marketplace={}, accountId={}, endpoint={}, method={}, uri={}",
          marketplaceType, accountId, endpointKey, method, uri
      );

      Path resultPath = downloader.download(
          marketplaceType,
          endpointKey,
          accountId,
          method,
          uri,
          headers,
          safe(body),
          target
      );

      long size = Files.size(resultPath);

      log.info(
          "Snapshot download completed: marketplace={}, accountId={}, endpoint={}, path={}, sizeBytes={}",
          marketplaceType, accountId, endpointKey, resultPath, size
      );

      return new Snapshot<>(elementType, resultPath);

    } catch (IOException ex) {
      log.error(
          "Snapshot download failed: marketplace={}, accountId={}, endpoint={}",
          marketplaceType, accountId, endpointKey, ex
      );
      throw new RuntimeException("Snapshot failed: " + endpointKey, ex);
    }
  }

  private Path planPath(long accountId, EndpointKey endpoint) {
    String endpointTag = sanitize(endpoint.tag());
    String marketplace = sanitize(marketplaceType.name().toLowerCase());

    String fileName = "%s.json".formatted(endpointTag);

    return baseDir()
        .resolve(marketplace)
        .resolve(Long.toString(accountId))
        .resolve(endpointTag)
        .resolve(fileName);
  }

  private String sanitize(String value) {
    return value == null ? "unknown" : value.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
  }

  private Path baseDir() {
    return properties.getStorage().getBaseDir();
  }
}
