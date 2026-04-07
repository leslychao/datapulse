package io.datapulse.execution.scheduling;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.persistence.DeferredActionEntity;
import io.datapulse.execution.persistence.DeferredActionRepository;
import io.datapulse.execution.config.ExecutionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeferredActionProcessor {

    private final DeferredActionRepository deferredActionRepository;
    private final ActionService actionService;
    private final ExecutionProperties properties;
    private final NamedParameterJdbcTemplate jdbc;

    private static final String DECISION_PRICES_SQL = """
            SELECT target_price, current_price
            FROM price_decision
            WHERE id = :decisionId
            """;

    @Scheduled(fixedDelayString = "${datapulse.execution.deferred-action-interval:PT30S}")
    @SchedulerLock(name = "execution_deferredActionProcessor", lockAtMostFor = "PT5M")
    @Transactional
    public void processDeferred() {
        cleanupExpired();
        processPendingDeferred();
    }

    private void processPendingDeferred() {
        List<DeferredActionEntity> ready = deferredActionRepository
                .findReadyToExecute(OffsetDateTime.now());

        for (var deferred : ready) {
            try {
                createActionFromDeferred(deferred);
                deferredActionRepository.delete(deferred);
                log.info("Deferred action processed: offerId={}, decisionId={}",
                        deferred.getMarketplaceOfferId(), deferred.getPriceDecisionId());
            } catch (Exception e) {
                log.error("Failed to process deferred action: id={}, offerId={}",
                        deferred.getId(), deferred.getMarketplaceOfferId(), e);
            }
        }
    }

    void createActionFromDeferred(DeferredActionEntity deferred) {
        boolean autoApprove =
                deferred.getExecutionMode() == ActionExecutionMode.SIMULATED;

        var params = new MapSqlParameterSource("decisionId", deferred.getPriceDecisionId());
        var prices = jdbc.query(DECISION_PRICES_SQL, params, (rs, rowNum) -> new BigDecimal[]{
                rs.getBigDecimal("target_price"),
                rs.getBigDecimal("current_price")
        });

        if (prices.isEmpty() || prices.get(0)[0] == null) {
            log.warn("Price decision not found or no target_price: decisionId={}",
                    deferred.getPriceDecisionId());
            return;
        }

        BigDecimal targetPrice = prices.get(0)[0];
        BigDecimal currentPrice = Objects.requireNonNullElse(prices.get(0)[1], BigDecimal.ZERO);

        actionService.createAction(
                deferred.getWorkspaceId(),
                deferred.getMarketplaceOfferId(),
                deferred.getPriceDecisionId(),
                deferred.getExecutionMode(),
                targetPrice,
                currentPrice,
                properties.getApprovalTimeoutHours(),
                autoApprove
        );
    }

    void cleanupExpired() {
        int deleted = deferredActionRepository.deleteExpired(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired deferred actions", deleted);
        }
    }
}
