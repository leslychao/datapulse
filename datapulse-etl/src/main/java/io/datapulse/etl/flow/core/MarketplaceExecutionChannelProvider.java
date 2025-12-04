package io.datapulse.etl.flow.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowContext;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Provides per-marketplace serialized channels backed by dedicated single-thread executors.
 * Each marketplace gets its own flow that guarantees in-order processing while allowing
 * different marketplaces to execute in parallel.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class MarketplaceExecutionChannelProvider {

  private final IntegrationFlowContext integrationFlowContext;

  private final Map<String, MessageChannel> channels = new ConcurrentHashMap<>();

  public MessageChannel resolveForMarketplace(String marketplaceKey) {
    return channels.computeIfAbsent(marketplaceKey, this::registerExecutionFlow);
  }

  private MessageChannel registerExecutionFlow(String marketplaceKey) {
    MessageChannel inputChannel = new DirectChannel();

    ThreadPoolTaskExecutor executor = buildSingleThreadExecutor(marketplaceKey);

    IntegrationFlow flow = IntegrationFlow.from(inputChannel)
        .channel(MessageChannels.executor(executor))
        .channel(EtlFlowConstants.CH_ETL_EXECUTION_PROCESS)
        .get();

    integrationFlowContext
        .registration(flow)
        .id("etl-execution-" + marketplaceKey)
        .register();

    return inputChannel;
  }

  private ThreadPoolTaskExecutor buildSingleThreadExecutor(String marketplaceKey) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(Integer.MAX_VALUE);
    executor.setThreadNamePrefix("etl-" + marketplaceKey.toLowerCase() + "-");
    executor.setThreadFactory(threadFactory(marketplaceKey));
    executor.initialize();
    return executor;
  }

  private ThreadFactory threadFactory(String marketplaceKey) {
    AtomicInteger counter = new AtomicInteger(0);
    return runnable -> new Thread(
        runnable,
        "etl-" + marketplaceKey.toLowerCase() + "-" + counter.incrementAndGet()
    );
  }
}
