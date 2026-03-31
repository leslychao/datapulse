package io.datapulse.api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    // ── Direct exchanges ────────────────────────────────────────────────────

    public static final String ETL_SYNC_EXCHANGE = "etl.sync";
    public static final String ETL_SYNC_WAIT_EXCHANGE = "etl.sync.wait";
    public static final String ETL_EVENTS_EXCHANGE = "datapulse.etl.events";
    public static final String PRICING_RUN_EXCHANGE = "pricing.run";
    public static final String PRICE_EXECUTION_EXCHANGE = "price.execution";
    public static final String PRICE_EXECUTION_WAIT_EXCHANGE = "price.execution.wait";
    public static final String PRICE_RECONCILIATION_EXCHANGE = "price.reconciliation";
    public static final String PRICE_RECONCILIATION_WAIT_EXCHANGE = "price.reconciliation.wait";
    public static final String PROMO_EXECUTION_EXCHANGE = "promo.execution";
    public static final String PROMO_EVALUATION_EXCHANGE = "promo.evaluation";

    // ── Queue names ─────────────────────────────────────────────────────────

    public static final String ETL_SYNC_QUEUE = "etl.sync";
    public static final String ETL_SYNC_WAIT_QUEUE = "etl.sync.wait";
    public static final String ETL_EVENTS_PRICING_QUEUE = "etl.events.pricing-worker";
    public static final String ETL_EVENTS_API_QUEUE = "etl.events.api";
    public static final String PRICING_RUN_QUEUE = "pricing.run";
    public static final String PRICE_EXECUTION_QUEUE = "price.execution";
    public static final String PRICE_EXECUTION_WAIT_QUEUE = "price.execution.wait";
    public static final String PRICE_RECONCILIATION_QUEUE = "price.reconciliation";
    public static final String PRICE_RECONCILIATION_WAIT_QUEUE = "price.reconciliation.wait";
    public static final String PROMO_EXECUTION_QUEUE = "promo.execution";
    public static final String PROMO_EVALUATION_QUEUE = "promo.evaluation";

    // ── DLX TTL defaults (ms) ───────────────────────────────────────────────

    private static final long ETL_WAIT_TTL = 1_500_000L;
    private static final long EXECUTION_WAIT_TTL = 60_000L;
    private static final long RECONCILIATION_WAIT_TTL = 60_000L;

    @Bean
    public Declarables rabbitTopology() {

        // ── Exchanges ───────────────────────────────────────────────────────

        var etlSyncExchange = new DirectExchange(ETL_SYNC_EXCHANGE, true, false);
        var etlSyncWaitExchange = new DirectExchange(ETL_SYNC_WAIT_EXCHANGE, true, false);
        var etlEventsExchange = new FanoutExchange(ETL_EVENTS_EXCHANGE, true, false);
        var pricingRunExchange = new DirectExchange(PRICING_RUN_EXCHANGE, true, false);
        var priceExecutionExchange = new DirectExchange(PRICE_EXECUTION_EXCHANGE, true, false);
        var priceExecutionWaitExchange = new DirectExchange(PRICE_EXECUTION_WAIT_EXCHANGE, true, false);
        var priceReconciliationExchange = new DirectExchange(PRICE_RECONCILIATION_EXCHANGE, true, false);
        var priceReconciliationWaitExchange = new DirectExchange(PRICE_RECONCILIATION_WAIT_EXCHANGE, true, false);
        var promoExecutionExchange = new DirectExchange(PROMO_EXECUTION_EXCHANGE, true, false);
        var promoEvaluationExchange = new DirectExchange(PROMO_EVALUATION_EXCHANGE, true, false);

        // ── Main queues ─────────────────────────────────────────────────────

        var etlSyncQueue = QueueBuilder.durable(ETL_SYNC_QUEUE).build();
        var pricingRunQueue = QueueBuilder.durable(PRICING_RUN_QUEUE).build();
        var priceExecutionQueue = QueueBuilder.durable(PRICE_EXECUTION_QUEUE).build();
        var priceReconciliationQueue = QueueBuilder.durable(PRICE_RECONCILIATION_QUEUE).build();
        var promoExecutionQueue = QueueBuilder.durable(PROMO_EXECUTION_QUEUE).build();
        var promoEvaluationQueue = QueueBuilder.durable(PROMO_EVALUATION_QUEUE).build();

        // ── Fanout queues ───────────────────────────────────────────────────

        var etlEventsPricingQueue = QueueBuilder.durable(ETL_EVENTS_PRICING_QUEUE).build();
        var etlEventsApiQueue = QueueBuilder.durable(ETL_EVENTS_API_QUEUE).build();

        // ── Wait queues (DLX → main exchange after TTL) ─────────────────────

        var etlSyncWaitQueue = QueueBuilder.durable(ETL_SYNC_WAIT_QUEUE)
                .deadLetterExchange(ETL_SYNC_EXCHANGE)
                .deadLetterRoutingKey(ETL_SYNC_QUEUE)
                .ttl((int) ETL_WAIT_TTL)
                .build();

        var priceExecutionWaitQueue = QueueBuilder.durable(PRICE_EXECUTION_WAIT_QUEUE)
                .deadLetterExchange(PRICE_EXECUTION_EXCHANGE)
                .deadLetterRoutingKey(PRICE_EXECUTION_QUEUE)
                .ttl((int) EXECUTION_WAIT_TTL)
                .build();

        var priceReconciliationWaitQueue = QueueBuilder.durable(PRICE_RECONCILIATION_WAIT_QUEUE)
                .deadLetterExchange(PRICE_RECONCILIATION_EXCHANGE)
                .deadLetterRoutingKey(PRICE_RECONCILIATION_QUEUE)
                .ttl((int) RECONCILIATION_WAIT_TTL)
                .build();

        // ── Bindings: direct exchanges → queues ─────────────────────────────

        Binding etlSyncBinding = BindingBuilder.bind(etlSyncQueue)
                .to(etlSyncExchange).with(ETL_SYNC_QUEUE);
        Binding pricingRunBinding = BindingBuilder.bind(pricingRunQueue)
                .to(pricingRunExchange).with(PRICING_RUN_QUEUE);
        Binding priceExecutionBinding = BindingBuilder.bind(priceExecutionQueue)
                .to(priceExecutionExchange).with(PRICE_EXECUTION_QUEUE);
        Binding priceReconciliationBinding = BindingBuilder.bind(priceReconciliationQueue)
                .to(priceReconciliationExchange).with(PRICE_RECONCILIATION_QUEUE);
        Binding promoExecutionBinding = BindingBuilder.bind(promoExecutionQueue)
                .to(promoExecutionExchange).with(PROMO_EXECUTION_QUEUE);
        Binding promoEvaluationBinding = BindingBuilder.bind(promoEvaluationQueue)
                .to(promoEvaluationExchange).with(PROMO_EVALUATION_QUEUE);

        // ── Bindings: wait exchanges → wait queues ──────────────────────────

        Binding etlSyncWaitBinding = BindingBuilder.bind(etlSyncWaitQueue)
                .to(etlSyncWaitExchange).with(ETL_SYNC_WAIT_QUEUE);
        Binding priceExecutionWaitBinding = BindingBuilder.bind(priceExecutionWaitQueue)
                .to(priceExecutionWaitExchange).with(PRICE_EXECUTION_WAIT_QUEUE);
        Binding priceReconciliationWaitBinding = BindingBuilder.bind(priceReconciliationWaitQueue)
                .to(priceReconciliationWaitExchange).with(PRICE_RECONCILIATION_WAIT_QUEUE);

        // ── Bindings: fanout exchange → consumer queues ─────────────────────

        Binding etlEventsPricingBinding = BindingBuilder.bind(etlEventsPricingQueue)
                .to(etlEventsExchange);
        Binding etlEventsApiBinding = BindingBuilder.bind(etlEventsApiQueue)
                .to(etlEventsExchange);

        return new Declarables(
                // exchanges
                etlSyncExchange, etlSyncWaitExchange, etlEventsExchange,
                pricingRunExchange,
                priceExecutionExchange, priceExecutionWaitExchange,
                priceReconciliationExchange, priceReconciliationWaitExchange,
                promoExecutionExchange, promoEvaluationExchange,

                // main queues
                etlSyncQueue, pricingRunQueue, priceExecutionQueue,
                priceReconciliationQueue, promoExecutionQueue, promoEvaluationQueue,

                // fanout queues
                etlEventsPricingQueue, etlEventsApiQueue,

                // wait queues
                etlSyncWaitQueue, priceExecutionWaitQueue, priceReconciliationWaitQueue,

                // bindings: direct
                etlSyncBinding, pricingRunBinding, priceExecutionBinding,
                priceReconciliationBinding, promoExecutionBinding, promoEvaluationBinding,

                // bindings: wait
                etlSyncWaitBinding, priceExecutionWaitBinding, priceReconciliationWaitBinding,

                // bindings: fanout
                etlEventsPricingBinding, etlEventsApiBinding
        );
    }
}
