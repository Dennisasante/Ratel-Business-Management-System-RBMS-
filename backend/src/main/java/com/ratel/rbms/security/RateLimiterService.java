package com.ratel.rbms.security;

import com.ratel.rbms.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory sliding-window rate limiter — fine for a single-instance
 * deployment (which is what this project's own hosting notes describe). If
 * this ever runs across multiple instances behind a load balancer, this
 * needs to move to something shared (Redis) since each instance would
 * otherwise track attempts independently and the effective limit would be
 * (instances × maxAttempts).
 *
 * Keyed by caller-supplied strings (e.g. "login:" + email) rather than IP,
 * since the goal is stopping credential-stuffing against a specific account
 * — IP-based throttling is arguably better handled at the reverse-proxy
 * level (Nginx, Cloudflare) in front of this, not duplicated here.
 */
@Component
public class RateLimiterService {

    private final Map<String, ConcurrentLinkedDeque<Instant>> attempts = new ConcurrentHashMap<>();

    /** Throws a 429 if the key has already hit maxAttempts within window. Doesn't record anything itself. */
    public void checkAllowed(String key, int maxAttempts, Duration window) {
        ConcurrentLinkedDeque<Instant> timestamps = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(window);

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() != null && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a few minutes and try again.");
            }
        }
    }

    public void recordAttempt(String key) {
        attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).addLast(Instant.now());
    }

    /** Call on success so a legitimate login right after a few typos doesn't stay half-throttled. */
    public void reset(String key) {
        attempts.remove(key);
    }
}
