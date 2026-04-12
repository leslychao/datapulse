package io.datapulse.etl.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.etl.config.IngestProperties;
import io.datapulse.etl.persistence.JobExecutionRow;
import io.datapulse.integration.persistence.MarketplaceConnectionEntity;
import io.datapulse.integration.persistence.MarketplaceConnectionRepository;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds {@link IngestContext} for a acquired job (credentials, checkpoint, DAG scope).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestSyncContextBuilder {

  private final CredentialResolver credentialResolver;
  private final CheckpointManager checkpointManager;
  private final ObjectMapper objectMapper;
  private final MarketplaceSyncStateRepository syncStateRepository;
  private final MarketplaceConnectionRepository connectionRepository;
  private final IngestProperties ingestProperties;
  private final Clock clock;

  public IngestContext build(JobExecutionRow job) {
    CredentialResolver.ResolvedCredentials creds = credentialResolver.resolve(
        job.getConnectionId());

    Map<EtlEventType, IngestContext.CheckpointEntry> checkpoint =
        checkpointManager.parse(job.getCheckpoint());

    Set<EtlEventType> scope = resolveScope(job);

    FactCaptureWindow factWindow = resolveFactCaptureWindow(job);

    String connectionMetadata = resolveConnectionMetadata(job.getConnectionId());

    return new IngestContext(
        job.getId(),
        job.getConnectionId(),
        creds.workspaceId(),
        creds.marketplace(),
        creds.credentials(),
        job.getEventType(),
        scope,
        checkpoint,
        factWindow.wbDateFrom(),
        factWindow.wbDateTo(),
        factWindow.ozonSince(),
        factWindow.ozonTo(),
        connectionMetadata);
  }

  private Set<EtlEventType> resolveScope(JobExecutionRow job) {
    if ("MANUAL_SYNC".equals(job.getEventType())) {
      Set<EtlEventType> manual = parseManualDomainScope(job.getParams());
      if (!manual.isEmpty()) {
        return manual;
      }
      return DagDefinition.fullSyncScope();
    }
    return DagDefinition.fullSyncScope();
  }

  /**
   * Parses {@code params.domains} (array of {@link EtlEventType} names) and expands hard
   * dependencies. Empty or missing domains → empty set (caller uses full sync scope).
   */
  private Set<EtlEventType> parseManualDomainScope(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return Set.of();
    }
    try {
      JsonNode root = objectMapper.readTree(paramsJson);
      JsonNode domainsNode = root.get("domains");
      if (domainsNode == null || !domainsNode.isArray() || domainsNode.isEmpty()) {
        return Set.of();
      }
      EnumSet<EtlEventType> seeds = EnumSet.noneOf(EtlEventType.class);
      for (JsonNode el : domainsNode) {
        if (!el.isTextual()) {
          continue;
        }
        String name = el.asText();
        try {
          seeds.add(EtlEventType.valueOf(name));
        } catch (IllegalArgumentException e) {
          log.warn("Ignoring unknown domain in job params: {}", name);
        }
      }
      if (seeds.isEmpty()) {
        return Set.of();
      }
      return DagDefinition.scopeWithHardDependencyClosure(seeds);
    } catch (JsonProcessingException e) {
      log.warn("Invalid job params JSON, treating as no domain filter: {}", e.getMessage());
      return Set.of();
    }
  }

  private FactCaptureWindow resolveFactCaptureWindow(JobExecutionRow job) {
    ZoneId zone = clock.getZone();
    OffsetDateTime now = OffsetDateTime.now(clock);
    LocalDate today = now.toLocalDate();

    OffsetDateTime fullHorizon = now.minusDays(ingestProperties.fullFactLookbackDays());
    OffsetDateTime incrementalHorizon = now.minusDays(ingestProperties.incrementalFactLookbackDays());

    OffsetDateTime startOdt;
    if ("INCREMENTAL".equals(job.getEventType())) {
      Optional<OffsetDateTime> lastSuccess = maxLastSuccessAt(job.getConnectionId());
      if (lastSuccess.isEmpty()) {
        startOdt = fullHorizon;
        log.debug(
            "Fact window: INCREMENTAL, no last_success_at → full lookback: connectionId={}, "
                + "days={}",
            job.getConnectionId(),
            ingestProperties.fullFactLookbackDays());
      } else {
        OffsetDateTime fromWatermark = lastSuccess.get().minus(ingestProperties.factSyncOverlap());
        if (fromWatermark.isAfter(now)) {
          startOdt = incrementalHorizon;
        } else if (fromWatermark.isBefore(incrementalHorizon)) {
          startOdt = incrementalHorizon;
        } else {
          startOdt = fromWatermark;
        }
        log.debug(
            "Fact window: INCREMENTAL, watermark={}, effectiveStart={}",
            lastSuccess.get(),
            startOdt);
      }
    } else {
      startOdt = fullHorizon;
      log.debug(
          "Fact window: {} → full lookback: connectionId={}, days={}",
          job.getEventType(),
          job.getConnectionId(),
          ingestProperties.fullFactLookbackDays());
    }

    LocalDate wbFrom = startOdt.atZoneSameInstant(zone).toLocalDate();
    if (wbFrom.isAfter(today)) {
      wbFrom = today;
    }

    return new FactCaptureWindow(wbFrom, today, startOdt, now);
  }

  private String resolveConnectionMetadata(long connectionId) {
    return connectionRepository.findById(connectionId)
        .map(MarketplaceConnectionEntity::getMetadata)
        .orElse(null);
  }

  private Optional<OffsetDateTime> maxLastSuccessAt(long connectionId) {
    List<MarketplaceSyncStateEntity> states =
        syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
    if (states.isEmpty()) {
      return Optional.empty();
    }
    return states.stream()
        .map(MarketplaceSyncStateEntity::getLastSuccessAt)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder());
  }

  private record FactCaptureWindow(
      LocalDate wbDateFrom,
      LocalDate wbDateTo,
      OffsetDateTime ozonSince,
      OffsetDateTime ozonTo) {}
}
