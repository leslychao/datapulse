package io.datapulse.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.api.config.RabbitTopologyConfig;
import io.datapulse.execution.domain.ExecutionCredentialResolver;
import io.datapulse.execution.domain.OfferExecutionContext;
import io.datapulse.execution.domain.ReconciliationService;
import io.datapulse.execution.domain.PriceReadAdapter;
import io.datapulse.execution.domain.PriceReadResult;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.integration.domain.MarketplaceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consumes RECONCILIATION_CHECK messages.
 * Reads current price from marketplace API, then delegates to ReconciliationService
 * to compare expected vs actual and transition action state.
 */
@Slf4j
@Component
public class PriceActionReconcileConsumer {

    private final PriceActionRepository actionRepository;
    private final ReconciliationService reconciliationService;
    private final ExecutionCredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final Map<MarketplaceType, PriceReadAdapter> readAdapters;

    public PriceActionReconcileConsumer(PriceActionRepository actionRepository,
                                       ReconciliationService reconciliationService,
                                       ExecutionCredentialResolver credentialResolver,
                                       ObjectMapper objectMapper,
                                       List<PriceReadAdapter> adapters) {
        this.actionRepository = actionRepository;
        this.reconciliationService = reconciliationService;
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
        this.readAdapters = adapters.stream()
                .collect(Collectors.toMap(PriceReadAdapter::marketplace, Function.identity()));
    }

    @RabbitListener(queues = RabbitTopologyConfig.PRICE_RECONCILIATION_QUEUE)
    public void onMessage(Message message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getBody());
            long actionId = payload.path("actionId").asLong();
            int attempt = payload.path("attempt").asInt(1);

            if (actionId <= 0) {
                log.error("Invalid actionId in reconciliation message: payload={}",
                        new String(message.getBody()));
                return;
            }

            log.info("Processing reconciliation: actionId={}, attempt={}", actionId, attempt);

            PriceActionEntity action = actionRepository.findById(actionId).orElse(null);
            if (action == null) {
                log.warn("Action not found for reconciliation: actionId={}", actionId);
                return;
            }

            OfferExecutionContext context = credentialResolver.resolve(action.getMarketplaceOfferId());

            PriceReadAdapter readAdapter = readAdapters.get(context.marketplaceType());
            if (readAdapter == null) {
                log.error("No read adapter for marketplace: {}", context.marketplaceType());
                return;
            }

            PriceReadResult readResult = readAdapter.readCurrentPrice(
                    context.connectionId(), context.marketplaceSku(), context.credentials());

            reconciliationService.processReconciliationCheck(
                    actionId, attempt, readResult.currentPrice(), readResult.rawSnapshot());

        } catch (Exception e) {
            log.error("Poison pill detected in price.reconciliation queue: messageId={}, error={}",
                    message.getMessageProperties().getMessageId(), e.getMessage(), e);
        }
    }
}
