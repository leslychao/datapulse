package io.datapulse.integration.domain.ratelimit;

import io.datapulse.platform.observability.MetricsFacade;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class MarketplaceRateLimiter {

    private static final String KEY_PREFIX = "rate:";
    private static final double IN_MEMORY_FALLBACK_FACTOR = 0.5;

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler;
    private final AimdRateAdjuster aimdController;
    private final MetricsFacade metricsFacade;
    private final DefaultRedisScript<Long> tokenBucketScript;

    private final ConcurrentHashMap<String, InMemoryBucket> fallbackBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> registeredGauges = new ConcurrentHashMap<>();

    public MarketplaceRateLimiter(
            StringRedisTemplate redisTemplate,
            @Qualifier("rateLimitScheduler") ScheduledExecutorService scheduler,
            AimdRateAdjuster aimdController,
            MetricsFacade metricsFacade) {
        this.redisTemplate = redisTemplate;
        this.scheduler = scheduler;
        this.aimdController = aimdController;
        this.metricsFacade = metricsFacade;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setResultType(Long.class);
    }

    @PostConstruct
    void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("redis/token-bucket.lua");
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        tokenBucketScript.setScriptText(script);
    }

    public CompletableFuture<Void> acquire(long connectionId, RateLimitGroup group) {
        registerGaugeIfAbsent(connectionId, group);
        double effectiveRate = aimdController.getCurrentRate(connectionId, group);
        int burst = aimdController.resolveBurst(group);
        String redisKey = KEY_PREFIX + connectionId + ":" + group.name();

        long waitMs;
        try {
            waitMs = tryAcquireRedis(redisKey, effectiveRate, burst);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for rate limiter, using in-memory fallback: connectionId={}, group={}",
                    connectionId, group);
            waitMs = tryAcquireInMemory(redisKey, effectiveRate * IN_MEMORY_FALLBACK_FACTOR, burst);
        } catch (Exception e) {
            log.error("Rate limiter Redis error, using in-memory fallback: connectionId={}, group={}",
                    connectionId, group, e);
            waitMs = tryAcquireInMemory(redisKey, effectiveRate * IN_MEMORY_FALLBACK_FACTOR, burst);
        }

        if (waitMs > 0) {
            metricsFacade.recordDuration(
                    "marketplace_rate_limit_wait_seconds",
                    Duration.ofMillis(waitMs),
                    "connection_id", String.valueOf(connectionId),
                    "rate_limit_group", group.name(),
                    "marketplace_type", group.getMarketplaceType().name());
        }

        if (waitMs <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), waitMs, TimeUnit.MILLISECONDS);
        return future;
    }

    public void onResponse(long connectionId, RateLimitGroup group, int httpStatus) {
        if (httpStatus == 429 || httpStatus == 420) {
            aimdController.onThrottle(connectionId, group);
            metricsFacade.incrementCounter(
                    "marketplace_rate_limit_throttled_total",
                    "connection_id", String.valueOf(connectionId),
                    "rate_limit_group", group.name(),
                    "marketplace_type", group.getMarketplaceType().name());
        } else if (httpStatus >= 200 && httpStatus < 300) {
            aimdController.onSuccess(connectionId, group);
        }
    }

    public double getCurrentRate(long connectionId, RateLimitGroup group) {
        return aimdController.getCurrentRate(connectionId, group);
    }

    private void registerGaugeIfAbsent(long connectionId, RateLimitGroup group) {
        String gaugeKey = connectionId + ":" + group.name();
        registeredGauges.computeIfAbsent(gaugeKey, k -> {
            metricsFacade.gauge(
                    "marketplace_rate_limit_current_rate",
                    () -> aimdController.getCurrentRate(connectionId, group),
                    "connection_id", String.valueOf(connectionId),
                    "rate_limit_group", group.name(),
                    "marketplace_type", group.getMarketplaceType().name());
            return Boolean.TRUE;
        });
    }

    private long tryAcquireRedis(String key, double rate, int burst) {
        long nowMs = System.currentTimeMillis();
        long ttlSeconds = Math.max((long) Math.ceil(burst / rate), 300);

        Long result = redisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(rate),
                String.valueOf(burst),
                String.valueOf(nowMs),
                String.valueOf(ttlSeconds));

        return result != null ? result : 0;
    }

    private long tryAcquireInMemory(String key, double rate, int burst) {
        InMemoryBucket bucket = fallbackBuckets.computeIfAbsent(key,
                k -> new InMemoryBucket(burst, rate));
        bucket.setRate(rate);
        return bucket.tryAcquire();
    }

    private static class InMemoryBucket {
        private volatile double tokens;
        private volatile double rate;
        private final int burst;
        private final AtomicLong lastRefillMs = new AtomicLong(System.currentTimeMillis());

        InMemoryBucket(int burst, double rate) {
            this.burst = burst;
            this.tokens = burst;
            this.rate = rate;
        }

        void setRate(double rate) {
            this.rate = rate;
        }

        synchronized long tryAcquire() {
            long now = System.currentTimeMillis();
            long last = lastRefillMs.get();
            double deltaMs = now - last;
            if (deltaMs > 0) {
                tokens = Math.min(tokens + (deltaMs / 1000.0) * rate, burst);
                lastRefillMs.set(now);
            }

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return 0;
            }

            double deficit = 1.0 - tokens;
            return (long) Math.ceil(deficit / rate * 1000);
        }
    }
}
