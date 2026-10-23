package com.nova.tech.bank.sphere.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Token bucket per key. Not shared between servers: use Redis (or Bucket4j) when you run several. */
@Component
public class RateLimiter {

    private record Bucket(double tokens, long lastMillis) {}

    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns 0 if the call is allowed, otherwise the number of seconds to wait. */
    public long tryConsume(String key, int capacity, Duration window) {
        long now = clock.millis();
        double refillPerMs = (double) capacity / window.toMillis();
        long[] wait = {0};

        buckets.compute(key, (k, old) -> {
            double tokens = (old == null)
                    ? capacity
                    : Math.min(capacity, old.tokens() + (now - old.lastMillis()) * refillPerMs);
            if (tokens >= 1) {
                wait[0] = 0;
                return new Bucket(tokens - 1, now);
            }
            wait[0] = (long) Math.ceil((1 - tokens) / refillPerMs / 1000.0);
            return new Bucket(tokens, now);
        });

        if (calls.incrementAndGet() % 1000 == 0) {            // forget idle clients
            buckets.entrySet().removeIf(e -> e.getValue().lastMillis() < now - 3_600_000);
        }
        return wait[0];
    }
}