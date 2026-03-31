package io.datapulse.integration.domain.ratelimit;

import io.datapulse.platform.observability.MetricsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Ozon per-product rate limiter: 10 price updates per hour per product.
 * Uses Redis sorted set as sliding window counter.
 * Key: product_rate:{connectionId}:{productId}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OzonProductRateLimiter {

    private static final String KEY_PREFIX = "product_rate:";
    private static final int MAX_UPDATES_PER_HOUR = 10;
    private static final long WINDOW_MS = Duration.ofHours(1).toMillis();
    private static final long TTL_SECONDS = Duration.ofMinutes(70).toSeconds();

    private final StringRedisTemplate redisTemplate;
    private final MetricsFacade metricsFacade;

    /**
     * Checks whether the product can be updated without exceeding the per-product limit.
     * Returns true if the product has budget remaining.
     * When Redis is unavailable, returns true (no proactive check; rely on 429 + backoff).
     */
    public boolean canUpdate(long connectionId, long productId) {
        String key = buildKey(connectionId, productId);
        try {
            long windowStart = System.currentTimeMillis() - WINDOW_MS;
            Long count = redisTemplate.opsForZSet().count(key, windowStart, Double.POSITIVE_INFINITY);
            if (count != null && count >= MAX_UPDATES_PER_HOUR) {
                metricsFacade.incrementCounter(
                        "ozon_product_rate_limit_exhausted_total",
                        "connection_id", String.valueOf(connectionId),
                        "product_id", String.valueOf(productId));
                return false;
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for Ozon per-product rate check, allowing update: connectionId={}, productId={}",
                    connectionId, productId);
            return true;
        } catch (Exception e) {
            log.error("Ozon per-product rate check failed, allowing update: connectionId={}, productId={}",
                    connectionId, productId, e);
            return true;
        }
    }

    /**
     * Records a successful price update for the product.
     * Cleans up expired entries and sets key TTL.
     */
    public void recordUpdate(long connectionId, long productId) {
        String key = buildKey(connectionId, productId);
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_MS);
            redisTemplate.expire(key, Duration.ofSeconds(TTL_SECONDS));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for Ozon per-product rate record: connectionId={}, productId={}",
                    connectionId, productId);
        } catch (Exception e) {
            log.error("Ozon per-product rate record failed: connectionId={}, productId={}",
                    connectionId, productId, e);
        }
    }

    private String buildKey(long connectionId, long productId) {
        return KEY_PREFIX + connectionId + ":" + productId;
    }
}
