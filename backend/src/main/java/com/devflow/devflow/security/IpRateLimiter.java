package com.devflow.devflow.security;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded, per-process fixed-window limiter. It deliberately stores no request data. */
public class IpRateLimiter {
    private final int maxTrackedKeys;
    private final Map<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);

    public IpRateLimiter(
            @org.springframework.beans.factory.annotation.Value("${devflow.rate-limit.max-tracked-keys:100000}") int maxTrackedKeys) {
        if (maxTrackedKeys < 1) {
            throw new IllegalArgumentException("Rate-limit tracked-key capacity must be positive");
        }
        this.maxTrackedKeys = maxTrackedKeys;
    }

    /** Returns seconds until retry is allowed, or zero when the request is accepted. */
    public synchronized long acquire(String bucket, int maximumRequests, Duration window, long nowMillis) {
        if (maximumRequests < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate-limit count and window must be positive");
        }

        long windowMillis = Math.max(1L, window.toMillis());
        Window current = windows.get(bucket);
        if (current == null || nowMillis - current.startedAtMillis >= windowMillis || nowMillis < current.startedAtMillis) {
            removeExpired(nowMillis);
            if (current == null) {
                makeRoom();
                current = new Window(nowMillis, windowMillis);
                windows.put(bucket, current);
            } else {
                current.startedAtMillis = nowMillis;
                current.requestCount = 0;
                current.windowMillis = windowMillis;
                windows.put(bucket, current);
            }
        }

        if (current.requestCount >= maximumRequests) {
            long remainingMillis = Math.max(1L, windowMillis - (nowMillis - current.startedAtMillis));
            return Math.max(1L, (remainingMillis + 999L) / 1000L);
        }
        current.requestCount++;
        return 0L;
    }

    private void removeExpired(long nowMillis) {
        Iterator<Window> iterator = windows.values().iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            if (nowMillis >= window.startedAtMillis && nowMillis - window.startedAtMillis >= window.windowMillis) {
                iterator.remove();
            }
        }
    }

    private void makeRoom() {
        while (windows.size() >= maxTrackedKeys) {
            Iterator<String> iterator = windows.keySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static final class Window {
        private long startedAtMillis;
        private long windowMillis;
        private int requestCount;

        private Window(long startedAtMillis, long windowMillis) {
            this.startedAtMillis = startedAtMillis;
            this.windowMillis = windowMillis;
        }
    }
}
