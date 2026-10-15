package com.familyvault.auth.security;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RequestRateLimitFilterTests {
    @Test void limitsAreIndependentAndResetOnlyAfterWindow() {
        AtomicLong now = new AtomicLong(1000);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        var limiter = new RequestRateLimitFilter(clock);
        assertTrue(limiter.allow("account-a", 2, Duration.ofSeconds(60)));
        assertTrue(limiter.allow("account-a", 2, Duration.ofSeconds(60)));
        assertFalse(limiter.allow("account-a", 2, Duration.ofSeconds(60)));
        assertTrue(limiter.allow("account-b", 2, Duration.ofSeconds(60)));
        now.addAndGet(60_000);
        assertTrue(limiter.allow("account-a", 2, Duration.ofSeconds(60)));
    }
}
