package io.datapulse.execution.domain.gateway;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ErrorClassifier;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.domain.PriceReadAdapter;
import io.datapulse.execution.domain.PriceReadResult;
import io.datapulse.execution.domain.PriceWriteAdapter;
import io.datapulse.execution.domain.PriceWriteResult;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Live gateway: dispatches to marketplace-specific write adapter,
 * performs read-after-write verification, translates PriceWriteResult → GatewayResult.
 */
@Slf4j
@Component
public class LivePriceActionGateway implements PriceActionGateway {

    private final Map<MarketplaceType, PriceWriteAdapter> writeAdapters;
    private final Map<MarketplaceType, PriceReadAdapter> readAdapters;
    private final ErrorClassifier errorClassifier;
    private final ExecutionProperties properties;

    public LivePriceActionGateway(List<PriceWriteAdapter> writeAdapterList,
                                  List<PriceReadAdapter> readAdapterList,
                                  ErrorClassifier errorClassifier,
                                  ExecutionProperties properties) {
        this.writeAdapters = writeAdapterList.stream()
                .collect(Collectors.toMap(PriceWriteAdapter::marketplace, Function.identity()));
        this.readAdapters = readAdapterList.stream()
                .collect(Collectors.toMap(PriceReadAdapter::marketplace, Function.identity()));
        this.errorClassifier = errorClassifier;
        this.properties = properties;
    }

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.LIVE;
    }

    @Override
    public GatewayResult execute(PriceActionEntity action, OfferExecutionContext context) {
        if (action.getExecutionMode() != ActionExecutionMode.LIVE) {
            throw new IllegalStateException(
                    "Live gateway received non-LIVE action: actionId=%d, mode=%s"
                            .formatted(action.getId(), action.getExecutionMode()));
        }

        PriceWriteAdapter adapter = writeAdapters.get(context.marketplaceType());
        if (adapter == null) {
            return GatewayResult.terminal(
                    ErrorClassification.NON_RETRIABLE,
                    "No write adapter for marketplace: " + context.marketplaceType(),
                    null, null
            );
        }

        try {
            PriceWriteResult writeResult = adapter.setPrice(
                    context.connectionId(),
                    context.marketplaceSku(),
                    action.getTargetPrice(),
                    context.credentials()
            );

            return translateWriteResult(writeResult, action, context);
        } catch (Exception e) {
            log.error("Write adapter exception: actionId={}, marketplace={}, error={}",
                    action.getId(), context.marketplaceType(), e.getMessage(), e);

            var classification = errorClassifier.classify(e);
            if (classification.isRetryable()) {
                return GatewayResult.retriable(
                        classification.classification(), classification.message(),
                        null, null
                );
            }
            if (classification.isUncertain()) {
                return GatewayResult.uncertain(null, null);
            }
            return GatewayResult.terminal(
                    classification.classification(), classification.message(),
                    null, null
            );
        }
    }

    private GatewayResult translateWriteResult(PriceWriteResult result,
                                                PriceActionEntity action,
                                                OfferExecutionContext context) {
        return switch (result.outcome()) {
            case CONFIRMED -> verifyAfterWrite(
                    action, context,
                    result.providerRequestSummary(), result.providerResponseSummary());

            case UNCERTAIN -> GatewayResult.uncertain(
                    result.providerRequestSummary(), result.providerResponseSummary());

            case REJECTED -> {
                if (context.marketplaceType() == MarketplaceType.OZON && result.errorCode() != null) {
                    var ozonClassification = errorClassifier.classifyOzonItemError(
                            result.errorCode(), result.errorMessage());
                    if (ozonClassification.isRetryable()) {
                        yield GatewayResult.retriable(
                                ozonClassification.classification(), ozonClassification.message(),
                                result.providerRequestSummary(), result.providerResponseSummary());
                    }
                }
                yield GatewayResult.terminal(
                        ErrorClassification.NON_RETRIABLE,
                        "Provider rejected: " + result.errorCode() + " - " + result.errorMessage(),
                        result.providerRequestSummary(), result.providerResponseSummary());
            }
        };
    }

    private GatewayResult verifyAfterWrite(PriceActionEntity action,
                                           OfferExecutionContext context,
                                           String requestSummary,
                                           String responseSummary) {
        PriceReadAdapter readAdapter = readAdapters.get(context.marketplaceType());
        if (readAdapter == null) {
            log.debug("No read adapter for {}, skipping read-after-write verification",
                    context.marketplaceType());
            return GatewayResult.confirmed(requestSummary, responseSummary);
        }

        long delayMs = properties.getReadAfterWriteDelay().toMillis();
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return GatewayResult.confirmed(requestSummary, responseSummary);
            }
        }

        try {
            PriceReadResult readResult = readAdapter.readCurrentPrice(
                    context.connectionId(), context.marketplaceSku(), context.credentials());

            if (readResult.currentPrice() == null) {
                log.warn("Read-after-write returned null price: actionId={}, sku={}",
                        action.getId(), context.marketplaceSku());
                return GatewayResult.confirmed(requestSummary, responseSummary);
            }

            boolean match = readResult.currentPrice().compareTo(action.getTargetPrice()) == 0;
            log.info("Read-after-write: actionId={}, target={}, actual={}, match={}",
                    action.getId(), action.getTargetPrice(),
                    readResult.currentPrice(), match);

            return GatewayResult.confirmedWithVerification(
                    requestSummary, responseSummary,
                    readResult.currentPrice(), match);
        } catch (Exception e) {
            log.warn("Read-after-write failed (best-effort): actionId={}, error={}",
                    action.getId(), e.getMessage());
            return GatewayResult.confirmed(requestSummary, responseSummary);
        }
    }
}
