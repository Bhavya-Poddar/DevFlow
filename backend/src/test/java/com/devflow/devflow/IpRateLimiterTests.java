package com.devflow.devflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.devflow.devflow.security.IpRateLimiter;

class IpRateLimiterTests {
    @Test
    void permitsRequestsUpToLimitAndRejectsAdditionalRequestsUntilWindowExpires() {
        IpRateLimiter limiter = new IpRateLimiter(10);
        Duration window = Duration.ofSeconds(30);

        assertEquals(0, limiter.acquire("login:192.0.2.1", 2, window, 1_000));
        assertEquals(0, limiter.acquire("login:192.0.2.1", 2, window, 1_001));
        assertEquals(30, limiter.acquire("login:192.0.2.1", 2, window, 1_002));
        assertEquals(0, limiter.acquire("login:192.0.2.1", 2, window, 31_000));
    }

    @Test
    void differentEndpointBucketsDoNotShareCountersAndCapacityRemainsBounded() {
        IpRateLimiter limiter = new IpRateLimiter(1);
        Duration window = Duration.ofMinutes(1);

        assertEquals(0, limiter.acquire("login:192.0.2.1", 1, window, 1_000));
        assertEquals(0, limiter.acquire("webhook:192.0.2.1", 1, window, 1_001));
        assertEquals(60, limiter.acquire("webhook:192.0.2.1", 1, window, 1_002));
    }

    @Test
    void rejectsInvalidLimits() {
        IpRateLimiter limiter = new IpRateLimiter(1);
        assertThrows(IllegalArgumentException.class,
                () -> limiter.acquire("login:192.0.2.1", 0, Duration.ofMinutes(1), 0));
    }
}
