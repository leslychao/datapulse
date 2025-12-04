package io.datapulse.etl.flow.core;

import static io.datapulse.domain.MessageCodes.ETL_REQUEST_INVALID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_ACCOUNT_ID;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_DATE_FROM;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_DATE_TO;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EVENT;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_EXPECTED_SOURCE_IDS;
import static io.datapulse.etl.flow.core.EtlFlowConstants.HDR_ETL_REQUEST_ID;

import io.datapulse.domain.MarketplaceEvent;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.SyncStatus;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.dto.ExecutionResult;
import io.datapulse.etl.dto.IngestResult;
import io.datapulse.etl.dto.OrchestrationBundle;
import io.micrometer.common.util.StringUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.store.MessageGroup;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrchestrationAggregationHelper {

  private final io.datapulse.core.i18n.I18nMessageService i18nMessageService;

  public boolean isFullGroup(MessageGroup group) {
    Message<?> sampleMessage = group.getOne();
    if (sampleMessage == null) {
      return true;
    }

    MessageHeaders headers = sampleMessage.getHeaders();
    String[] expectedSourceIdsArray =
        Optional.ofNullable(headers.get(HDR_ETL_EXPECTED_SOURCE_IDS, String[].class))
            .orElseGet(() -> new String[0]);
    List<String> expectedSourceIds = List.of(expectedSourceIdsArray);
    int expected = Optional.of(expectedSourceIds).map(List::size).orElse(0);

    return group.size() >= expected;
  }

  public OrchestrationBundle buildBundle(MessageGroup group) {
    List<IngestResult> results = group
        .getMessages()
        .stream()
        .map(Message::getPayload)
        .filter(ExecutionResult.class::isInstance)
        .map(ExecutionResult.class::cast)
        .map(ExecutionResult::ingestResult)
        .toList();

    Message<?> sample = group.getOne();
    MessageHeaders headers = sample != null
        ? sample.getHeaders()
        : new MessageHeaders(Map.of());

    String requestId = Optional.ofNullable(headers.get(HDR_ETL_REQUEST_ID, String.class))
        .orElseGet(
            () -> Optional.ofNullable(group.getGroupId()).map(Object::toString).orElse(null));
    Long accountId = headers.get(HDR_ETL_ACCOUNT_ID, Long.class);
    String eventValue = headers.get(HDR_ETL_EVENT, String.class);
    LocalDate dateFrom = headers.get(HDR_ETL_DATE_FROM, LocalDate.class);
    LocalDate dateTo = headers.get(HDR_ETL_DATE_TO, LocalDate.class);
    String[] expectedSourceIdsArray = Optional.ofNullable(
        headers.get(HDR_ETL_EXPECTED_SOURCE_IDS, String[].class)
    ).orElseGet(() -> new String[0]);
    List<String> expectedSourceIds = List.of(expectedSourceIdsArray);

    MarketplaceEvent event = MarketplaceEvent.fromString(eventValue);
    if (event == null) {
      throw new AppException(ETL_REQUEST_INVALID, "event=" + eventValue);
    }

    Set<String> ingestSourceIds = results.stream()
        .map(IngestResult::sourceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    List<String> missingSourceIds = expectedSourceIds.stream()
        .filter(Objects::nonNull)
        .filter(id -> !ingestSourceIds.contains(id))
        .toList();

    boolean hasMissingSources = !missingSourceIds.isEmpty();
    boolean hasError = results.stream().anyMatch(IngestResult::isError);

    SyncStatus syncStatus;

    if (hasError || hasMissingSources) {
      syncStatus = SyncStatus.ERROR;
    } else {
      syncStatus = SyncStatus.SUCCESS;
    }

    Set<String> failedSourceIds = results.stream()
        .filter(IngestResult::isError)
        .map(IngestResult::sourceId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    failedSourceIds.addAll(missingSourceIds);

    List<String> errorMessages = new ArrayList<>(results.stream()
        .filter(IngestResult::isError)
        .map(IngestResult::errorMessage)
        .filter(Objects::nonNull)
        .distinct()
        .toList());

    if (hasMissingSources) {
      String ids = String.join(",", missingSourceIds);
      String message = i18nMessageService.userMessage(
          MessageCodes.ETL_NO_INGEST_RESULTS,
          ids
      );
      errorMessages.add(message);
    }

    List<String> distinctErrorMessages = errorMessages.stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .toList();

    String errorMessageValue = String.join("; ", distinctErrorMessages);

    return new OrchestrationBundle(
        requestId,
        accountId,
        event,
        dateFrom,
        dateTo,
        syncStatus,
        String.join(",", failedSourceIds),
        errorMessageValue,
        results,
        null
    );
  }

  public EtlSyncAuditDto buildAuditDto(OrchestrationBundle bundle) {
    EtlSyncAuditDto dto = new EtlSyncAuditDto();
    dto.setRequestId(bundle.requestId());
    dto.setAccountId(bundle.accountId());
    dto.setEvent(bundle.event().name());
    dto.setDateFrom(bundle.dateFrom());
    dto.setDateTo(bundle.dateTo());
    dto.setStatus(bundle.syncStatus());
    dto.setFailedSources(bundle.failedSourceIds());
    dto.setErrorMessage(bundle.errorMessage());
    return dto;
  }
}
