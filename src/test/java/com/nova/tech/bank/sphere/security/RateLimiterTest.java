package com.nova.tech.bank.sphere.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    /** A clock we can move by hand. */
    static class TestClock extends Clock {
        private long millis = 1_000_000L;
        void advanceSeconds(long s) { millis += s * 1000; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    private final TestClock clock = new TestClock();
    private final RateLimiter limiter = new RateLimiter(clock);
    private final Duration minute = Duration.ofMinutes(1);

    @Test
    void allowsUpToTheCapacity_thenAsksToWait() {
        for (int i = 0; i < 3; i++) assertEquals(0, limiter.tryConsume("k", 3, minute));
        assertEquals(20, limiter.tryConsume("k", 3, minute));       // one token returns every 20 s
    }

    @Test
    void tokensComeBackOverTime() {
        for (int i = 0; i < 3; i++) limiter.tryConsume("k", 3, minute);
        assertTrue(limiter.tryConsume("k", 3, minute) > 0);
        clock.advanceSeconds(20);
        assertEquals(0, limiter.tryConsume("k", 3, minute));
        assertTrue(limiter.tryConsume("k", 3, minute) > 0);
    }

    @Test
    void differentKeysAreIndependent() {
        for (int i = 0; i < 3; i++) limiter.tryConsume("a", 3, minute);
        assertTrue(limiter.tryConsume("a", 3, minute) > 0);
        assertEquals(0, limiter.tryConsume("b", 3, minute));
    }

    @Test
    void longIdleTimeNeverAllowsMoreThanTheCapacity() {
        clock.advanceSeconds(3600);
        for (int i = 0; i < 3; i++) assertEquals(0, limiter.tryConsume("k", 3, minute));
        assertTrue(limiter.tryConsume("k", 3, minute) > 0);
    }
}