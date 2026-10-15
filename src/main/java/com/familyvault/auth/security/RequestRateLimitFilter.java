package com.familyvault.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Bounded single-instance limiter. Multi-replica deployments must enforce shared quotas at the gateway. */
public final class RequestRateLimitFilter extends OncePerRequestFilter {
    private record Bucket(long expiresAt, int count) {}
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;

    public RequestRateLimitFilter() { this(Clock.systemUTC()); }
    public RequestRateLimitFilter(Clock clock) { this.clock = clock; }

    public synchronized boolean allow(String key, int limit, Duration window) {
        long now = clock.millis();
        if (buckets.size() >= 10_000) {
            buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            if (buckets.size() >= 10_000 && !buckets.containsKey(key)) return false;
        }
        Bucket current = buckets.get(key);
        if (current == null || current.expiresAt() <= now) {
            buckets.put(key, new Bucket(now + window.toMillis(), 1));
            return true;
        }
        if (current.count() >= limit) return false;
        buckets.put(key, new Bucket(current.expiresAt(), current.count() + 1));
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        boolean auth = (path.startsWith("/api/auth/") || path.startsWith("/auth/")) && "POST".equals(request.getMethod());
        boolean ai = path.equals("/api/chat/ask") && "POST".equals(request.getMethod());
        boolean vault = path.startsWith("/api/vault/") && "POST".equals(request.getMethod());
        boolean invitation = path.contains("/invitations") && "POST".equals(request.getMethod());
        if (auth || ai || vault || invitation) {
            String category = ai ? "ai" : vault ? "vault" : invitation ? "invite" : "auth";
            String ipKey = category + ":ip:" + request.getRemoteAddr();
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            String principal = authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;
            boolean allowed = allow(ipKey, auth ? 20 : 15, Duration.ofMinutes(1));
            if (allowed && principal != null) {
                allowed = allow(category + ":user:" + SecurityTokens.hash(principal), ai ? 10 : 15, Duration.ofMinutes(1));
            }
            if (!allowed) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please wait and try again.\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
