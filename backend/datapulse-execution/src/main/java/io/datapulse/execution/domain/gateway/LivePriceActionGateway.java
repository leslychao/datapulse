package io.datapulse.execution.domain.gateway;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ErrorClassifier;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.domain.adapter.PriceWriteAdapter;
import io.datapulse.execution.domain.adapter.PriceWriteResult;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Live gateway: dispatches to marketplace-specific write adapter,
 * translates PriceWriteResult → GatewayResult.
 */
@Slf4j
@Component
public class LivePriceActionGateway implements PriceActionGateway {

    private final Map<MarketplaceType, PriceWriteAdapter> writeAdapters;
    private final ErrorClassifier errorClassifier;

    public LivePriceActionGateway(List<PriceWriteAdapter> adapters, ErrorClassifier errorClassifier) {
        this.writeAdapters = adapters.stream()
                .collect(Collectors.toMap(PriceWriteAdapter::marketplace, Function.identity()));
        this.errorClassifier = errorClassifier;
    }

    @Override
    public ActionExecutionMode executionMode() {
        return ActionExecutionMode.LIVE;
    }

    @Override
    public GatewayResult execute(PriceActionEntity action, OfferExecutionContext context) {
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

            return translateWriteResult(writeResult, context);
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
                                                OfferExecutionContext context) {
        return switch (result.outcome()) {
            case CONFIRMED -> GatewayResult.confirmed(
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
}
