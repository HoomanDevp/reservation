package com.azki.reservation.filter;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that applies rate limiting to API requests.
 * Currently, applies a global rate limit; could be extended to do per-client limiting.
 */
@Component
@Order(1)
@ConditionalOnProperty(value = "reservation.rate-limiting.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Bucket bucket;

    public RateLimitFilter(Bucket bucket) {
        this.bucket = bucket;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Only apply rate limiting to reservation API endpoints
        if (request.getRequestURI().startsWith("/api/reservations")) {
            if (bucket.tryConsume(1)) {
                // Request allowed, continue chain
                filterChain.doFilter(request, response);
            } else {
                // Rate limit exceeded
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Rate limit exceeded. Please try again later.");
                response.getWriter().flush();
            }
        } else {
            // Not a rate-limited endpoint
            filterChain.doFilter(request, response);
        }
    }
}
