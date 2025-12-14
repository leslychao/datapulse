package io.datapulse.marketplaces.adapter;

import static io.datapulse.domain.MessageCodes.DOWNLOAD_FAILED;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.exception.AppException;
import io.datapulse.marketplaces.config.MarketplaceProperties;
import io.datapulse.marketplaces.dto.Snapshot;
import io.datapulse.marketplaces.endpoint.EndpointAuthScope;
import io.datapulse.marketplaces.endpoint.EndpointKey;
import io.datapulse.marketplaces.endpoint.EndpointRef;
import io.datapulse.marketplaces.endpoint.EndpointsResolver;
import io.datapulse.marketplaces.http.HttpHeaderProvider;
import io.datapulse.marketplaces.service.AuthAccountIdResolver;
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
  private final AuthAccountIdResolver authAccountIdResolver;

  protected final <R> Snapshot<R> doGet(
      long accountId,
      EndpointKey key,
      Class<R> elementType
  ) {
    return execute(accountId, key, null, HttpMethod.GET, null, null, elementType);
  }

  protected final <R> Snapshot<R> doGet(
      long accountId,
      EndpointKey key,
      Map<String, ?> queryParams,
      Class<R> elementType
  ) {
    return execute(accountId, key, null, HttpMethod.GET, queryParams, null, elementType);
  }

  protected final <R> Snapshot<R> doPost(
      long accountId,
      EndpointKey key,
      Map<String, ?> body,
      Class<R> elementType
  ) {
    return execute(accountId, key, null, HttpMethod.POST, null, body, elementType);
  }

  protected final <R> Snapshot<R> doPostPartitioned(
      long accountId,
      EndpointKey key,
      Map<String, ?> body,
      String partitionKey,
      Class<R> elementType
  ) {
    return execute(accountId, key, partitionKey, HttpMethod.POST, null, body, elementType);
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
      long targetAccountId,
      EndpointKey endpointKey,
      String partitionKey,
      HttpMethod method,
      Map<String, ?> queryParams,
      Map<String, ?> body,
      Class<R> elementType
  ) {
    EndpointAuthScope scope = properties.get(marketplaceType).authScope(endpointKey);
    long authAccountId = authAccountIdResolver.resolveAuthAccountId(scope, targetAccountId);

    try {
      EndpointRef ref = resolveEndpoint(endpointKey, method, queryParams);
      URI uri = ref.uri();

      HttpHeaders headers = headerProvider.build(marketplaceType, authAccountId);
      Path target = planPath(authAccountId, endpointKey, partitionKey);

      if (Files.exists(target)) {
        long size = Files.size(target);
        log.info(
            "Snapshot reused from local cache: marketplace={}, targetAccountId={}, authAccountId={}, endpoint={}, partitionKey={}, path={}, sizeBytes={}",
            marketplaceType, targetAccountId, authAccountId, endpointKey, partitionKey, target, size
        );
        return new Snapshot<>(elementType, target);
      }

      log.info(
          "Starting snapshot download: marketplace={}, targetAccountId={}, authAccountId={}, endpoint={}, partitionKey={}, method={}, uri={}",
          marketplaceType, targetAccountId, authAccountId, endpointKey, partitionKey, method, uri
      );

      Path resultPath = downloader.download(
          marketplaceType,
          endpointKey,
          authAccountId,
          method,
          uri,
          headers,
          safe(body),
          target
      );

      long size = Files.size(resultPath);

      log.info(
          "Snapshot download completed: marketplace={}, targetAccountId={}, authAccountId={}, endpoint={}, partitionKey={}, path={}, sizeBytes={}",
          marketplaceType, targetAccountId, authAccountId, endpointKey, partitionKey, resultPath, size
      );

      return new Snapshot<>(elementType, resultPath);

    } catch (IOException ex) {
      log.error(
          "Snapshot download failed: marketplace={}, targetAccountId={}, authAccountId={}, endpoint={}, partitionKey={}",
          marketplaceType, targetAccountId, authAccountId, endpointKey, partitionKey, ex
      );
      throw new AppException(DOWNLOAD_FAILED, endpointKey, ex);
    }
  }

  private Path planPath(long authAccountId, EndpointKey endpoint, String partitionKey) {
    String endpointTag = sanitize(endpoint.tag());
    String marketplace = sanitize(marketplaceType.name().toLowerCase());

    String fileName = partitionKey == null || partitionKey.isBlank()
        ? "%s.json".formatted(endpointTag)
        : "%s_%s.json".formatted(endpointTag, sanitize(partitionKey));

    return baseDir()
        .resolve(marketplace)
        .resolve(Long.toString(authAccountId))
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
