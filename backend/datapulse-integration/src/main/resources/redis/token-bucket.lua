-- Token bucket rate limiter (atomic Lua script)
-- KEYS[1] = rate:{connection_id}:{rate_limit_group}
-- ARGV[1] = rate (tokens per second)
-- ARGV[2] = burst (max tokens)
-- ARGV[3] = now_ms (current time in milliseconds)
-- ARGV[4] = ttl_seconds (key expiration)
-- Returns: wait_ms (0 = token granted immediately, >0 = wait this many ms)

local key = KEYS[1]
local rate = tonumber(ARGV[1])
local burst = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local ttl_seconds = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(data[1])
local last_refill = tonumber(data[2])

if tokens == nil then
    tokens = burst
    last_refill = now_ms
end

-- Refill tokens based on elapsed time
local delta_ms = now_ms - last_refill
if delta_ms > 0 then
    local added = delta_ms / 1000.0 * rate
    tokens = math.min(tokens + added, burst)
    last_refill = now_ms
end

if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(last_refill))
    redis.call('EXPIRE', key, ttl_seconds)
    return 0
else
    -- Calculate wait time until next token is available
    local deficit = 1 - tokens
    local wait_ms = math.ceil(deficit / rate * 1000)
    redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(last_refill))
    redis.call('EXPIRE', key, ttl_seconds)
    return wait_ms
end
