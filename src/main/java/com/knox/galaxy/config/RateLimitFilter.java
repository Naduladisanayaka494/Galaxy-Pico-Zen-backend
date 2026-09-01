package com.knox.galaxy.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting on the auth endpoints — confirmed completely absent
 * before this. Keyed by IP only, not IP+username: reading the request body to
 * extract a username here would mean consuming and re-wrapping the input
 * stream, which is real complexity for a second layer on top of a limit
 * that's already meaningful on its own. A distributed attack spread across
 * many IPs would evade this; that's a real gap, not a design decision — flag
 * it if it becomes a problem, since fixing it means a proper WAF/CDN-level
 * control, not more application code.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/platform/auth/login",
            // Reset flow: throttled to blunt both address-enumeration probing
            // and brute-forcing the emailed token.
            "/api/auth/forgot-password", "/api/auth/reset-password");
    private static final int CAPACITY = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!PROTECTED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + request.getRequestURI();
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L + 1;
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many attempts. Try again in " + retryAfterSeconds + "s.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(CAPACITY, io.github.bucket4j.Refill.intervally(CAPACITY, WINDOW)))
                .build();
    }

    /** Trusts X-Forwarded-For's first hop — fine behind a single reverse proxy
     *  you control; if that topology changes, this needs revisiting. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
